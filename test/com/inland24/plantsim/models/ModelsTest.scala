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

  behavior of "PowerPlantType"

  "PowerPlantType" should "fetch proper PowerPlantType instances" in {
    PowerPlantType.toString(PowerPlantType.OnOffType) === "OnOffType"
    PowerPlantType.toString(PowerPlantType.RampUpType) === "RampUpType"
    PowerPlantType.toString(PowerPlantType.UnknownType) === "UnknownType"

    PowerPlantType.fromString("OnOffType") === PowerPlantType.OnOffType
    PowerPlantType.fromString("RampUpType") === PowerPlantType.RampUpType
    PowerPlantType.fromString("UnknownType") === PowerPlantType.UnknownType
  }

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

  behavior of "DispatchCommand"

  "DispatchCommand#reads" should "fail parsing for a DispatchCommand invalid PowerPlantType" in {
    val invalidPowerPlantType =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "turnOn",
        |  "value" : true,
        |  "powerPlantType" : "SomeShitPowerPlantType"
        |}
      """.stripMargin

    Json.parse(invalidPowerPlantType).validate[DispatchCommand].fold(
      _ => {
        // Nothing to check
      },
      _ => {
        fail(s"expected the parsing of an invalid DispatchCommand " +
          s"$invalidPowerPlantType to fail, but it succeeded")
      }
    )
  }

  "DispatchCommand#reads" should "read the Json and parse a valid DispatchCommand for OnOffType" in {
    val dispatchOnOffType =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "turnOn",
        |  "value" : true,
        |  "powerPlantType" : "OnOffType"
        |}
      """.stripMargin

    Json.parse(dispatchOnOffType).validate[DispatchCommand].fold(
      errors => {
        fail(s"valid DispatchCommand should have successfully parsed, " +
          s"but failed unexpectedly with errors $errors")
      },
      dispatchCommand => {
        dispatchCommand.powerPlantId == 2
      }
    )
  }

  "DispatchCommand#reads" should "error when parsing an invalid DispatchCommand for OnOffType" in {
    val invalidCommand =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "some shit",
        |  "value" : true,
        |  "powerPlantType" : "OnOffType"
        |}
      """.stripMargin

    val invalidValue =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "turnOn",
        |  "value" : "XXX",
        |  "powerPlantType" : "OnOffType"
        |}
      """.stripMargin

    Json.parse(invalidCommand).validate[DispatchCommand].fold(
      _ => {
        // Nothing to check!
      },
      _ => {
        fail(s"expected the parsing of an invalid DispatchCommand $invalidCommand to fail, but it succeeded")
      }
    )

    Json.parse(invalidValue).validate[DispatchCommand].fold(
      _ => {
        // Nothing to check!
      },
      _ => {
        fail(s"expected the parsing of an invalid DispatchCommand $invalidValue to fail, but it succeeded")
      }
    )
  }

  "DispatchCommand#reads" should "read the Json and parse a valid DispatchCommand for RampUpType" in {
    val dispatchRampUpType =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "dispatch",
        |  "value" : 20.0,
        |  "powerPlantType" : "RampUpType"
        |}
      """.stripMargin

    Json.parse(dispatchRampUpType).validate[DispatchCommand].fold(
      errors => {
        fail(s"valid DispatchCommand should have successfully parsed, " +
          s"but failed unexpectedly with errors $errors")
      },
      dispatchCommand => {
        dispatchCommand.powerPlantId == 2
      }
    )
  }

  "DispatchCommand#reads" should "error when parsing an invalid DispatchCommand for RampUpType" in {
    val invalidCommand =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "some shit",
        |  "value" : 10,
        |  "powerPlantType" : "RampUpType"
        |}
      """.stripMargin

    val invalidValue =
      """
        |{
        |  "powerPlantId" : 2,
        |  "command" : "dispatch",
        |  "value" : true,
        |  "powerPlantType" : "RampUpType"
        |}
      """.stripMargin

    Json.parse(invalidCommand).validate[DispatchCommand].fold(
      _ => {
        // Nothing to check!
      },
      _ => {
        fail(s"expected the parsing of an invalid DispatchCommand $invalidCommand to fail, but it succeeded")
      }
    )

    Json.parse(invalidValue).validate[DispatchCommand].fold(
      _ => { // errors
        // Nothing to check!
      },
      _ => { // success
        fail(s"expected the parsing of an invalid DispatchCommand $invalidValue to fail, but it succeeded")
      }
    )
  }

  behavior of "ReturnToNormalCommand"

  "ReturnToNormalCommand#reads" should "parse a Valid ReturnToNormalCommand" in {
    val returnToNormalCommand =
      """
        | { "powerPlantId": 2 }
      """.stripMargin

    Json.parse(returnToNormalCommand).validate[ReturnToNormalCommand].fold(
      _ => { // errors
        fail(s"expected a valid ReturnToNormalCommand $returnToNormalCommand " +
          s"to have validated successfully, but it did not!")
      },
      _ => { // success
        // Nothing to check!
      }
    )
  }

  "ReturnToNormalCommand#reads" should "fail parsing for an in-valid ReturnToNormalCommand" in {
    val returnToNormalCommand =
      """
        | { "": 2 }
      """.stripMargin

    Json.parse(returnToNormalCommand).validate[ReturnToNormalCommand].fold(
      _ => { // errors
        // Nothing to check!
      },
      _ => { // success
        fail(s"expected a in-valid ReturnToNormalCommand $returnToNormalCommand " +
          s"to have failed validation, but the validation was successful, please analyze!")
      }
    )
  }
}