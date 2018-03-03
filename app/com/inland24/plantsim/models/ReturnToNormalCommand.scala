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

import play.api.libs.json._

trait ReturnToNormal extends PowerPlantCommand {

  def powerPlantId: Int
  def toPowerValue: Option[Double]

  override def commandName: String = "ReturnToNormalCommand"
}
case class ReturnToNormalCommand(
    powerPlantId: Int,
    toPowerValue: Option[Double] = None
) extends ReturnToNormal
object ReturnToNormal {

  def apply(id: Int) = ReturnToNormalCommand(
    powerPlantId = id
  )
  /* TODO: Use this classes later
  case class RTNOnOffTypePowerPlant(
    powerPlantId: Int,
    toPowerValue: Option[Double] = None
  ) extends ReturnToNormalCommand

  case class RTNRampUpTypePowerPlant(
    powerPlantId: Int,
    toPowerValue: Option[Double] = None
  ) */

  implicit def jsonReads = new Reads[ReturnToNormalCommand] {

    def reads(json: JsValue): JsResult[ReturnToNormalCommand] = {
      if ((json \ "powerPlantId").asOpt[Int].isEmpty) {
        JsError(
          JsPath \ "ReturnToNormalCommand is missing powerPlantId key",
          "powerPlantId"
        )
      } else {
        JsSuccess(
          ReturnToNormalCommand((json \ "powerPlantId").as[Int])
        )
      }
    }
  }
}
