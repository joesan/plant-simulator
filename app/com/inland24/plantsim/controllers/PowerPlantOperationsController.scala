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

import com.inland24.plantsim.core.{AppBindings, EventsWebSocketActor}
import com.inland24.plantsim.models._
import monix.execution.FutureUtils.extensions._
import play.api.libs.json.JsError
import play.api.mvc.{ControllerComponents, WebSocket}
import akka.actor.ActorRef
import akka.pattern.ask
import com.inland24.plantsim.models.PowerPlantActorMessage.TelemetrySignalsMessage
import com.inland24.plantsim.streams.EventsStream.DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes
import play.api.libs.streams.ActorFlow

// TODO: pass in this execution context via AppBindings
import monix.execution.Scheduler.Implicits.global

import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class PowerPlantOperationsController(
    bindings: AppBindings,
    val controllerComponents: ControllerComponents)
    extends ControllerBase {

  // Place a reference to the underlying ActorSystem
  private implicit val system = bindings.actorSystem
  private implicit val timeout: akka.util.Timeout = 3.seconds
  private implicit val materializer = bindings.materializer

  // TODO: This could return a list of supported API's
  def home = Action { implicit request =>
    Ok("The API is ready")
  }

  // Utility to resolve an actor reference
  private def actorFor(powerPlantId: Int): Future[Option[ActorRef]] = {
    system
      .actorSelection(
        s"akka://application/user/*/${bindings.appConfig.appName}-$powerPlantId")
      .resolveOne(2.seconds)
      .materialize
      .map {
        case Success(actorRef) => Some(actorRef)
        case Failure(_)        => None
      }
  }

  private def sendCommand(actorRef: ActorRef,
                          id: Int,
                          command: PowerPlantCommand) = {
    actorRef ! command
    Future.successful {
      Accepted(
        Json.obj(
          "message" -> s"${command.commandName} accepted for PowerPlant with id $id")
      ).enableCors
    }
  }

  /**
    * THIS IS A SPECIAL ENDPOINT WHERE THE ONLY PURPOSE IS TO TEST
    * THE RE-ACTIVATION OF EVENT STREAMS IN CASE OF ABRUPT FAILURES
    *
    * BEWARE WHEN USING THIS ENDPOINT! ALTHOUGH IT DOES NOT HURT CALLING IT
    * THIS IS JUST TO DEMONSTRATE THE RESILIENCE TO FAILURES
    *
    * @param id The id of the PowerPlant that mimics an error scenario
    * @return   Always a HTTP 200
    */
  def kill(id: Int) = Action.async { _ =>
    val actorRef = scala.concurrent.Await.result(actorFor(id), 5.seconds).get
    actorRef ! DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes
    Future.successful(
      Ok("*** ###### Fnickug ---- YOU WANTED TO SPOIL MY PARTY *******......."))
  }

  // TODO: Re-Work for unit testability!
  def returnToNormalPowerPlant(id: Int) = Action.async(parse.tolerantJson) {
    request =>
      request.body
        .validate[ReturnToNormalCommand]
        .fold(
          errors => {
            Future.successful {
              BadRequest(
                Json.obj("status" -> "error",
                         "message" -> JsError.toJson(errors))
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
  def dispatchPowerPlant(id: Int) = Action.async(parse.tolerantJson) {
    request =>
      request.body
        .validate[DispatchCommand]
        .fold(
          errors => {
            Future.successful {
              BadRequest(
                Json.obj("status" -> "error",
                         "message" -> JsError.toJson(errors))
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
        (actorRef ? TelemetrySignalsMessage)
          .mapTo[Map[String, String]]
          .map(signals => Ok(Json.prettyPrint(Json.toJson(signals))).enableCors)
    }
  }

  def events(someId: Option[Int]) = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      EventsWebSocketActor.props(
        EventsWebSocketActor.eventsAndAlerts(someId, bindings.globalChannel),
        out
      )
    }
  }

  def signals(id: Int) = WebSocket.acceptOrResult[String, String] { _ =>
    actorFor(id).map {
      case None =>
        Left(Forbidden)
      case Some(powerPlantActorRef) =>
        Right(ActorFlow.actorRef { out =>
          EventsWebSocketActor.props(
            EventsWebSocketActor.telemetrySignals(id, powerPlantActorRef),
            out
          )
        })
    }
  }
}
