/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.inland24.plantsim.controllers

import com.inland24.plantsim.core.AppBindings
import com.inland24.plantsim.core.SupervisorActor.TelemetrySignals
import com.inland24.plantsim.models._
import monix.execution.FutureUtils.extensions._
import play.api.libs.json.JsError
import play.api.mvc.{Action, Controller, Result}
import akka.actor.ActorRef
import akka.pattern.ask

// TODO: pass in this execution context via AppBindings
import monix.execution.Scheduler.Implicits.global

import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class PowerPlantOperationsController(bindings: AppBindings)
  extends Controller with ControllerBase {

  // Place a reference to the underlying ActorSystem
  private val system = bindings.actorSystem
  implicit val timeout: akka.util.Timeout = 3.seconds

  // TODO: This could return a list of supported API's
  def home = Action { implicit request =>
    Ok("The API is ready")
  }

  // Utility to resolve an actor reference
  def actorFor(powerPlantId: Int): Future[Option[ActorRef]] = {
    system.actorSelection(s"akka://application/user/*/${bindings.appConfig.appName}-$powerPlantId")
      .resolveOne(2.seconds)
      .materialize
      .map {
        case Success(actorRef) => Some(actorRef)
        case Failure(_) => None
      }
  }

  def sendCommand(actorRef: ActorRef, id: Int, command: PowerPlantCommand) = {
    actorRef ! command
    Future.successful {
      Accepted(
        Json.obj("message" -> s"${command.commandName} accepted for PowerPlant with id $id")
      ).enableCors
    }
  }

  // TODO: Re-Work for unit testability!
  def returnToNormalPowerPlant(id: Int) = Action.async(parse.tolerantJson) { request =>
    request.body.validate[ReturnToNormalCommand].fold(
      errors => {
        Future.successful{
          BadRequest(
            Json.obj("status" -> "error", "message" -> JsError.toJson(errors))
          ).enableCors
        }
      },
      returnToNormalCommand => {
        actorFor(id) flatMap {
          case None =>
            Future.successful {
              NotFound(s"HTTP 404 :: PowerPlant with ID $id not found").enableCors
            }
          case Some(actorRef) =>
            sendCommand(actorRef, id, returnToNormalCommand)
        }
      }
    )
  }

  // TODO: Re-Work for unit testability!
  def dispatchPowerPlant(id: Int) = Action.async(parse.tolerantJson) { request =>
    request.body.validate[DispatchCommand].fold(
      errors => {
        Future.successful{
          BadRequest(
            Json.obj("status" -> "error", "message" -> JsError.toJson(errors))
          ).enableCors
        }
      },
      dispatchCommand => {
        actorFor(id) flatMap {
          case None =>
            Future.successful {
              NotFound(s"HTTP 404 :: PowerPlant with ID $id not found").enableCors
            }
          case Some(actorRef) =>
            sendCommand(actorRef, id, dispatchCommand)
        }
      }
    )
  }

  def powerPlantSignals(id: Int) = Action.async {
    actorFor(id) flatMap {
      case None =>
        Future.successful(
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found").enableCors
        )
      case Some(actorRef) =>
        (actorRef ? TelemetrySignals)
          .mapTo[Map[String, String]]
          .map(signals =>
            Ok(Json.prettyPrint(
                JsObject(
                  Seq("powerPlantId" -> JsString(id.toString)) ++ signals.map {
                    case (key, value) => key -> JsString(value)
                  }
                )
              )
            ).enableCors
          )
    }
  }
}