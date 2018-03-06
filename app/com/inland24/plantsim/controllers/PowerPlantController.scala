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
import com.inland24.plantsim.models.PowerPlantType.UnknownType
import com.inland24.plantsim.models.{
  PowerPlantConfig,
  PowerPlantFilter,
  PowerPlantType,
  toPowerPlantConfig
}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import com.inland24.plantsim.models._
import monix.execution.FutureUtils.extensions._

import scala.concurrent.Future
import scala.util.{Failure, Success}

// TODO: pass in this execution context via AppBindings
import monix.execution.Scheduler.Implicits.global

class PowerPlantController(bindings: AppBindings,
                           val controllerComponents: ControllerComponents)
    extends ControllerBase {

  private val dbService = bindings.dbService

  def powerPlantDetails(id: Int): Action[AnyContent] = Action.async {
    dbService.powerPlantById(id).runAsync.flatMap {
      case None =>
        Future.successful(
          NotFound(s"HTTP 404 :: PowerPlant with ID $id not found").enableCors
        )
      case Some(powerPlantRow) =>
        Future.successful(
          Ok(Json.prettyPrint(Json.toJson(toPowerPlantConfig(powerPlantRow)))).enableCors
        )
    }
  }

  // TODO: Check implementation!
  def createNewPowerPlant: Action[JsValue] = Action.async(parse.tolerantJson) {
    request =>
      request.body
        .validate[PowerPlantConfig]
        .fold(
          errors => {
            Future.successful(
              BadRequest(Json.obj(
                "message" -> s"Invalid PowerPlantConfig $errors")).enableCors
            )
          },
          powerPlantCfg => {
            // We expect the caller to set the Id to 0, otherwise we fail create
            if (powerPlantCfg.id == 0) {
              dbService
                .createNewPowerPlant(powerPlantCfg)
                .runAsync
                .materialize
                .map {
                  case Failure(ex) =>
                    InternalServerError(s"Error updating PowerPlant " +
                      s"Reason => ${ex.getMessage}").enableCors
                  case Success(result) =>
                    result match {
                      case Left(errorMessage) =>
                        BadRequest(Json.obj("message" ->
                          s"Error When creating a new PowerPlant :: Error Message: $errorMessage")).enableCors
                      case Right(createdConfig) =>
                        Ok(Json.prettyPrint(Json.toJson(createdConfig))).enableCors
                    }
                }
            } else {
              Future.successful(BadRequest(Json.obj("message" ->
                s"invalid PowerPlantConfig! Please set the id of the Powerplant to 0 for create new PowerPlant")).enableCors)
            }
          }
        )
  }

  def updatePowerPlant(id: Int): Action[JsValue] =
    Action.async(parse.tolerantJson) { request =>
      request.body
        .validate[PowerPlantConfig]
        .fold(
          errors => {
            Future.successful(
              BadRequest(
                Json.obj(
                  "message" -> s"invalid PowerPlantConfig ${errors.mkString(",")}")
              ).enableCors
            )
          },
          success => {
            dbService
              .updatePowerPlant(success)
              .runAsync
              .materialize
              .map {
                case Failure(ex) =>
                  InternalServerError(s"Error updating PowerPlant " +
                    s"Reason => ${ex.getMessage}").enableCors
                case Success(result) =>
                  result match {
                    case Left(errorMessage) =>
                      BadRequest(Json.obj("message" ->
                        s"Error when updating PowerPlant Error Message :: $errorMessage")).enableCors
                    case Right(updatedConfig) =>
                      Ok(Json.prettyPrint(Json.toJson(updatedConfig))).enableCors
                  }
              }
          }
        )
    }

  def powerPlants(onlyActive: Boolean, page: Int): Action[AnyContent] =
    Action.async {
      val filter =
        PowerPlantFilter(onlyActive = Some(onlyActive), pageNumber = page)
      dbService.searchPowerPlants(filter).runAsync.materialize.map {
        case Success(powerPlantsSeqRow) =>
          val collected = powerPlantsSeqRow.collect {
            case powerPlantRow if powerPlantRow.powerPlantType != UnknownType =>
              toPowerPlantConfig(powerPlantRow)
          }
          Ok(Json.prettyPrint(Json.toJson(collected))).enableCors

        case Failure(ex) =>
          InternalServerError(
            s"Error fetching all PowerPlant's " +
              s"from the database => ${ex.getMessage}").enableCors
      }
    }

  def searchPowerPlants(onlyActive: Option[Boolean],
                        page: Int,
                        powerPlantType: Option[String] = None,
                        powerPlantName: Option[String] = None,
                        orgName: Option[String] = None): Action[AnyContent] =
    Action.async {

      val mappedPowerPlantType = powerPlantType.flatMap(someType => {
        val typ = PowerPlantType.fromString(someType)
        if (typ == UnknownType) None
        else Some(typ)
      })

      val filter = PowerPlantFilter(
        onlyActive = onlyActive,
        powerPlantType = mappedPowerPlantType,
        orgName = orgName,
        pageNumber = page
      )

      dbService.searchPowerPlants(filter).runAsync.materialize.map {
        case Success(powerPlantsSeqRow) =>
          val collected = powerPlantsSeqRow.collect {
            case powerPlantRow if powerPlantRow.powerPlantType != UnknownType =>
              toPowerPlantConfig(powerPlantRow)
          }
          Ok(Json.prettyPrint(Json.toJson(collected))).enableCors

        case Failure(ex) =>
          InternalServerError(
            s"Error fetching all PowerPlant's " +
              s"from the database => ${ex.getMessage}").enableCors
      }
    }
}
