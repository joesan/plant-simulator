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
import com.inland24.plantsim.core.SupervisorActor.TelemetrySignals
import com.inland24.plantsim.models.PowerPlantType.UnknownType
import com.inland24.plantsim.models._
import play.api.mvc.{Action, Controller, Result}
import monix.execution.FutureUtils.extensions._
import play.api.libs.json.JsError

// TODO: pass in this execution context via AppBindings
import monix.execution.Scheduler.Implicits.global

import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class PowerPlantController(bindings: AppBindings) extends Controller {

  implicit class RichResult (result: Result) {
    def enableCors =  result.withHeaders(
      "Access-Control-Allow-Origin" -> "*"
      , "Access-Control-Allow-Methods" -> "OPTIONS, GET, POST, PUT, DELETE, HEAD"   // OPTIONS for pre-flight
      , "Access-Control-Allow-Headers" -> "Accept, Content-Type, Origin, X-Json, X-Prototype-Version, X-Requested-With" //, "X-My-NonStd-Option"
      , "Access-Control-Allow-Credentials" -> "true"
    )
  }

  // Place a reference to the underlying ActorSystem
  private val system = bindings.actorSystem
  private val dbService = bindings.dbService

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
      )
    }
  }

  def appConfig = Action.async {
    Future.successful(
      Ok(Json.prettyPrint(
        Json.toJson(bindings.appConfig))
      )
    )
  }

  def powerPlantDetails(id: Int) = Action.async {
    dbService.powerPlantById(id).flatMap {
      case None =>
        Future.successful(
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found").enableCors
        )
      case Some(powerPlantRow) =>
        Future.successful(
          Ok(Json.prettyPrint(
            Json.toJson(toPowerPlantConfig(powerPlantRow)))
          ).enableCors
        )
    }
  }

  def powerPlants(onlyActive: Boolean, page: Int) = Action.async {
    dbService.allPowerPlantsPaginated(onlyActive, page).materialize.map {
      case Success(powerPlantsSeqRow) =>
        val collected = powerPlantsSeqRow.collect {
          case powerPlantRow
            if powerPlantRow.powerPlantType != UnknownType =>
            toPowerPlantConfig(powerPlantRow)
        }
        Ok(Json.prettyPrint(Json.toJson(collected))).enableCors

      case Failure(ex) =>
        InternalServerError(s"Error fetching all PowerPlant's " +
          s"from the database => ${ex.getMessage}").enableCors
    }
  }

  def searchPowerPlants(onlyActive: Option[Boolean], onlyDisabled: Option[Boolean], page: Int,
    powerPlantType: Option[String] = None, powerPlantName: Option[String] = None,
    orgName: Option[String] = None) = Action.async {

    val mappedPowerPlantType = powerPlantType.flatMap(someType => {
      val typ = PowerPlantType.fromString(someType)
      if (typ == UnknownType) None
      else Some(typ)
    })

    val filter = onlyActive match {
      case Some(_) => PowerPlantFilter(
        onlyActive = onlyActive,
        powerPlantType = mappedPowerPlantType,
        orgName = orgName,
        pageNumber = page
      )
      case None => PowerPlantFilter(
        powerPlantType = mappedPowerPlantType,
        orgName = orgName,
        pageNumber = page,
        onlyDisabled = onlyDisabled
      )
    }

    dbService.powerPlantsPaginated(filter).materialize.map {
      case Success(powerPlantsSeqRow) =>
        val collected = powerPlantsSeqRow.collect {
          case powerPlantRow
            if powerPlantRow.powerPlantType != UnknownType =>
            toPowerPlantConfig(powerPlantRow)
        }
        Ok(Json.prettyPrint(Json.toJson(collected))).enableCors

      case Failure(ex) =>
        InternalServerError(s"Error fetching all PowerPlant's " +
          s"from the database => ${ex.getMessage}").enableCors
    }
  }

  // TODO: Check implementation!
  def createNewPowerPlant = Action.async(parse.tolerantJson) { request =>
    request.body.validate[PowerPlantConfig].fold(
      errors => {
        Future.successful(
          BadRequest(Json.obj("message" -> s"invalid PowerPlantConfig $errors")).enableCors
        )
      },
      success => {
        toPowerPlantRow(success) match {
          case None => Future.successful(
            BadRequest(Json.obj("message" -> s"invalid PowerPlantConfig ")).enableCors // TODO: fix errors
          )
          case Some(row) =>
            dbService.newPowerPlant(row).materialize.map {
              case Success(insertedRecordId) =>
                Ok("TODO: Send a Success JSON back with the id of the newly inserted record").enableCors
              case Failure(ex) =>
                UnprocessableEntity(
                  Json.obj("message" -> s"Could not create new PowerPlant because of ${ex.getMessage}")
                ).enableCors
            }
        }
      }
    )
  }

  // TODO: Re-Work for unit testability!
  def returnToNormalPowerPlant(id: Int) = Action.async(parse.tolerantJson) { request =>
    request.body.validate[ReturnToNormalCommand].fold(
      errors => {
        Future.successful{
          BadRequest(
            Json.obj("status" -> "error", "message" -> JsError.toJson(errors))
          )
        }
      },
      returnToNormalCommand => {
        actorFor(id) flatMap {
          case None =>
            Future.successful {
              NotFound(s"HTTP 404 :: PowerPlant with ID $id not found")
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
          )
        }
      },
      dispatchCommand => {
        actorFor(id) flatMap {
          case None =>
            Future.successful {
              NotFound(s"HTTP 404 :: PowerPlant with ID $id not found")
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
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found")
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
            )
          )
    }
  }
}