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

package com.inland24.plantsim.models

import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import play.api.libs.json._


trait DispatchCommand {
  def powerPlantId: Int
  def powerPlantType: PowerPlantType
  def command: String
}
object DispatchCommand {

  case class DispatchOnOffPowerPlant(
    powerPlantId: Int,
    powerPlantType: PowerPlantType,
    command: String,
    value: Boolean
  ) extends DispatchCommand

  case class DispatchRampUpPowerPlant(
    powerPlantId: Int,
    powerPlantType: PowerPlantType,
    command: String,
    value: Double
  ) extends DispatchCommand

  implicit def jsonReads = new Reads[DispatchCommand] {

    private def isTurnOnOffValid(json: JsValue) = {
      val isValidCommand = "turnon" == (json \ "command").as[String].toLowerCase
      val isValidValue = (json \ "value").asOpt[Boolean].nonEmpty
      isValidCommand && isValidValue
    }

    private def isRampUpValid(json: JsValue) = {
      val isValidCommand = "dispatch" == (json \ "command").as[String].toLowerCase
      val isValidValue = (json \ "value").asOpt[Double].nonEmpty
      isValidCommand && isValidValue
    }

    def reads(json: JsValue): JsResult[DispatchCommand] = {
      if((json \ "powerPlantType").asOpt[String].isEmpty ||
        (json \ "powerPlantId").asOpt[Int].isEmpty ||
        (json \ "command").asOpt[String].isEmpty ||
        (json \ "value").asOpt[String].isEmpty
      ) {
        JsError(
          JsPath \ "DispatchCommand is missing one of required keys",
          "powerPlantType, powerPlantId, command, value"
        )
      }

      PowerPlantType.fromString((json \ "powerPlantType").as[String]) match {
        case OnOffType if isTurnOnOffValid(json) =>
          JsSuccess(
            DispatchOnOffPowerPlant(
              powerPlantType = OnOffType,
              powerPlantId = (json \ "powerPlantId").as[Int],
              command = (json \ "command").as[String],
              value = (json \ "value").as[Boolean]
            )
          )
        case RampUpType if isRampUpValid(json) =>
          JsSuccess(
            DispatchRampUpPowerPlant(
              powerPlantType = OnOffType,
              powerPlantId = (json \ "powerPlantId").as[Int],
              command = (json \ "command").as[String],
              value = (json \ "value").as[Double]
            )
          )
        case _ =>
          JsError(
            JsPath \ "DispatchCommand is missing one of required keys",
            "powerPlantType, powerPlantId, command, value"
          )
      }
    }
  }
}