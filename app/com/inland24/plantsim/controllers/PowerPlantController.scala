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
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantFilter, PowerPlantType, toPowerPlantConfig, toPowerPlantRow}
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, Result}
import com.inland24.plantsim.models._
import monix.execution.FutureUtils.extensions._

import scala.concurrent.Future
import scala.util.{Failure, Success}

// TODO: pass in this execution context via AppBindings
import monix.execution.Scheduler.Implicits.global


class PowerPlantController(bindings: AppBindings) extends Controller {

  implicit class RichResult (result: Result) {
    def enableCors =  result.withHeaders(
      "Access-Control-Allow-Origin" -> "*"
      , "Access-Control-Allow-Methods" -> "OPTIONS, GET, POST, PUT, DELETE, HEAD"   // OPTIONS for pre-flight
      , "Access-Control-Allow-Headers" -> "Accept, Content-Type, Origin, X-Json, X-Prototype-Version, X-Requested-With" //, "X-My-NonStd-Option"
      , "Access-Control-Allow-Credentials" -> "true"
    )
  }

  private val dbService = bindings.dbService

  def powerPlantDetails(id: Int) = Action.async {
    dbService.powerPlantById(id).runAsync.flatMap {
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

  def updatePowerPlant(id: Int) = Action.async(parse.tolerantJson) { request =>
    request.body.validate[PowerPlantConfig].fold(
      errors => {
        Future.successful(
          BadRequest(Json.obj("message" -> s"invalid PowerPlantConfig $errors")).enableCors
        )
      },
      success => {
        dbService.updatePowerPlant(success).runAsync.materialize.map {
          case Failure(ex) =>
            InternalServerError(Json.obj("message" -> s"invalid PowerPlantConfig $ex")).enableCors
          case Success(result) =>
            result match {
              case Left(errorMessage) =>
                BadRequest(Json.obj("message" -> s"invalid PowerPlantConfig $errorMessage")).enableCors
              case Right(updatedConfig) =>
                Ok(Json.toJson(updatedConfig))
            }
        }
      }
    )
  }

  def powerPlants(onlyActive: Boolean, page: Int) = Action.async {
    val filter = PowerPlantFilter(onlyActive = Some(onlyActive), pageNumber = page)
    dbService.searchPowerPlants(filter).runAsync.materialize.map {
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

  def searchPowerPlants(onlyActive: Option[Boolean], page: Int,
    powerPlantType: Option[String] = None, powerPlantName: Option[String] = None,
    orgName: Option[String] = None) = Action.async {

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
            dbService.createNewPowerPlant(row).runAsync.materialize.map {
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
}
