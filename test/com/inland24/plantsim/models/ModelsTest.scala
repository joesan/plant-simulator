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

import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig, UnknownConfig}
import org.scalatest.FlatSpec
import play.api.libs.json.Json

import scala.concurrent.duration._


class ModelsTest extends FlatSpec {

  "appConfigWrites" should "produce a JSON for the given AppConfig" in {
    val expectedJson =
      """
        |{
        |   "environment":"default",
        |   "application":"plant-simulator",
        |   "dbConfig":{
        |      "databaseDriver":"org.h2.Driver",
        |      "databaseUrl":"jdbc:h2:mem:power-simulator;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        |      "databaseUser":"***********",
        |      "databasePass":"***********"
        |   }
        |}
      """.stripMargin

    // default environment is loaded from application.conf
    val appCfg = AppConfig.load()
    val actualJson = appConfigWrites.writes(appCfg).toString()

    expectedJson === actualJson
  }

  "OnOffTypeConfigWrites" should "Serialize and De-Serialize for the given OnOffTypeConfig" in {
    val expectedJsonOnOffType =
      """
        |{
        |   "powerPlantId":1,
        |   "powerPlantName":"SomeName",
        |   "minPower":10,
        |   "maxPower":20,
        |   "powerPlantType":"OnOffType"
        |}
      """.stripMargin

    val onOffTypePlantCfg = OnOffTypeConfig(
      id = 1,
      name = "SomeName",
      maxPower = 20.0,
      minPower = 10.0,
      powerPlantType = PowerPlantType.OnOffType
    )
    powerPlantCfgFormat.writes(onOffTypePlantCfg) === expectedJsonOnOffType

    val actualOnOffTypeCfg = powerPlantCfgFormat.reads(
      Json.parse(expectedJsonOnOffType)
    ).get
    actualOnOffTypeCfg === onOffTypePlantCfg
  }

  "RampUpTypeConfigWrites" should "Serialize and De-Serialize for the given RampUpTypeConfig" in {
    val expectedJsonRampUpType =
      """
        |{
        |   "powerPlantId":1,
        |   "powerPlantName":"SomeName",
        |   "minPower":10,
        |   "maxPower":20,
        |   "rampRateInSeconds": 2,
        |   "rampPowerRate": 2.0,
        |   "powerPlantType":"RampUpType"
        |}
      """.stripMargin

    val rampUpTypePlantCfg = RampUpTypeConfig(
      id = 1,
      name = "SomeName",
      maxPower = 20.0,
      minPower = 10.0,
      rampPowerRate = 2.0,
      rampRateInSeconds = 2.seconds,
      powerPlantType = PowerPlantType.RampUpType
    )
    powerPlantCfgFormat.writes(rampUpTypePlantCfg) === expectedJsonRampUpType

    val actualRampUpTypeCfg = powerPlantCfgFormat.reads(
      Json.parse(expectedJsonRampUpType)
    ).get
    actualRampUpTypeCfg === rampUpTypePlantCfg
  }

  "UnknownConfigTypeConfigWrites" should "Serialize and De-Serialize for the given UnknownConfigTypeConfig" in {
    val expectedJsonUnknownType =
      """
        |{
        |   "powerPlantId":1,
        |   "powerPlantName":"SomeName",
        |   "minPower":10,
        |   "maxPower":20,
        |   "powerPlantType":"SomeShitType"
        |}
      """.stripMargin

    val unknownPlantCfg = UnknownConfig(
      id = 1,
      name = "SomeName",
      maxPower = 20.0,
      minPower = 10.0,
      powerPlantType = PowerPlantType.UnknownType
    )
    powerPlantCfgFormat.writes(unknownPlantCfg) === expectedJsonUnknownType

    val actualRampUpTypeCfg = powerPlantCfgFormat.reads(
      Json.parse(expectedJsonUnknownType)
    ).get
    actualRampUpTypeCfg === unknownPlantCfg
  }
}