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

import java.util.Locale

trait DispatchCommand extends PowerPlantCommand {
  def powerPlantId: Int
  def powerPlantType: PowerPlantType
  def command: String

  override def commandName: String = "DispatchCommand"
}
object DispatchCommand {

  final case class DispatchOnOffPowerPlant(
      powerPlantId: Int,
      powerPlantType: PowerPlantType,
      command: String,
      value: Boolean
  ) extends DispatchCommand

  final case class DispatchRampUpPowerPlant(
      powerPlantId: Int,
      powerPlantType: PowerPlantType,
      command: String,
      value: Double
  ) extends DispatchCommand

  implicit def jsonReads: Reads[DispatchCommand] = new Reads[DispatchCommand] {

    private def isTurnOnOffValid(json: JsValue) = {
      val isValidCommand = "turnon" == (json \ "command")
        .as[String]
        .toLowerCase(Locale.ENGLISH)
      val isValidValue = (json \ "value").asOpt[Boolean].nonEmpty
      isValidCommand && isValidValue
    }

    private def isRampUpValid(json: JsValue) = {
      val isValidCommand = "dispatch" == (json \ "command")
        .as[String]
        .toLowerCase(Locale.ENGLISH)
      val isValidValue = (json \ "value").asOpt[Double].nonEmpty
      isValidCommand && isValidValue
    }

    def reads(json: JsValue): JsResult[DispatchCommand] = {
      if ((json \ "powerPlantType").asOpt[String].isEmpty ||
          (json \ "powerPlantId").asOpt[Int].isEmpty ||
          (json \ "command").asOpt[String].isEmpty) {
        JsError(
          JsPath \ "DispatchCommand is missing one of required keys",
          "powerPlantType, powerPlantId, command, value ****"
        )
      } else {
        PowerPlantType.fromString((json \ "powerPlantType").as[String]) match {
          case OnOffType if isTurnOnOffValid(json) =>
            for {
              powerPlantId <- (json \ "powerPlantId").validate[Int]
              command <- (json \ "command").validate[String]
              value <- (json \ "value").validate[Boolean]
            } yield {
              DispatchOnOffPowerPlant(
                powerPlantType = OnOffType,
                powerPlantId = powerPlantId,
                command = command,
                value = value
              )
            }
          case RampUpType if isRampUpValid(json) =>
            for {
              powerPlantId <- (json \ "powerPlantId").validate[Int]
              command <- (json \ "command").validate[String]
              value <- (json \ "value").validate[Double]
            } yield {
              DispatchRampUpPowerPlant(
                powerPlantType = RampUpType,
                powerPlantId = powerPlantId,
                command = command,
                value = value
              )
            }
          case _ =>
            JsError(
              JsPath \ "DispatchCommand is missing one of required keys",
              "powerPlantType, powerPlantId, command, value"
            )
        }
      }
    }
  }
}
