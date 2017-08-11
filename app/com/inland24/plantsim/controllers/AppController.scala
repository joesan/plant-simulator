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

import akka.actor.ActorRef
import akka.pattern.ask
import com.inland24.plantsim.core.AppBindings
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeSimulatorActor.StateRequest
import com.inland24.plantsim.services.simulator.onOffType.PowerPlantState
import com.inland24.plantsim.models._
import play.api.mvc.{Action, Controller}
import monix.execution.FutureUtils.extensions._
import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class AppController(bindings: AppBindings) extends Controller {

  // Place a reference to the underlying ActorSystem
  implicit val system = bindings.actorSystem
  val dbService = bindings.dbService
  // TODO: pass in this execution context
  import monix.execution.Scheduler.Implicits.global
  implicit val timeout: akka.util.Timeout = 3.seconds

  def home = Action { implicit request =>
    Ok("The API is ready")
  }

  // Utility to resolve an actor reference
  def actorFor(powerPlantId: Int): Future[Option[ActorRef]] = {
    system.actorSelection(s"${bindings.appConfig.appName}-$powerPlantId")
      .resolveOne(2.seconds)
      .materialize
      .map {
        case Success(actorRef) => Some(actorRef)
        case Failure(_) => None
      }
  }

  def powerPlantDetails(id: Int) = Action.async {
    dbService.powerPlantById(id).flatMap {
      case None =>
        Future.successful(
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found")
        )
      case Some(powerPlantRow) =>
        Future.successful(
          Ok(Json.toJson(
            toPowerPlantConfig(powerPlantRow))
          )
        )
    }
  }

  def powerPlantStatus(id: Int) = Action.async {
    actorFor(id) flatMap {
      case None =>
        Future.successful(
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found")
        )
      case Some(actorRef) =>
        (actorRef ? StateRequest)
          .mapTo[PowerPlantState]
          .map(powerPlantState =>
            Ok(
              Json.prettyPrint(
                JsObject(
                  Seq(
                    "powerPlantId" -> JsString(powerPlantState.powerPlantId.toString)
                  ) ++ powerPlantState.signals.map {
                    case (key, value) => key -> JsString(value)
                  }
                )
              )
            )
          )
    }
  }
}