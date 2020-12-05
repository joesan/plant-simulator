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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.inland24.plantsim.core.AppBindings
import com.inland24.plantsim.services.database.DBServiceSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler.Implicits.global
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.matchers.should

import scala.concurrent.Future
import org.scalatestplus.play._
import play.api.libs.json._
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import scala.util.{Failure, Success}

class PowerPlantControllerTest
    extends TestKit(ActorSystem("PowerPlantControllerTest"))
    with should.Matchers
    with AnyFeatureSpecLike
    with OptionValues
    with WsScalaTestClient
    with Results
    with BeforeAndAfterAll
    with DBServiceSpec {

  val bindings: AppBindings = AppBindings.apply(system)
  private val controllerComponents = stubControllerComponents()
  val controller = new PowerPlantController(bindings, controllerComponents)

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    super.h2SchemaDrop()
    TestKit.shutdownActorSystem(system)
  }

  // ApplicationConfigController test
  Feature("ApplicationConfigController") {
    Scenario("give the appropriate config back when asked") {
      // We are using the application.test.conf (Look in the DBServiceSpec.scala)
      val result: Future[Result] =
        new ApplicationController(bindings.appConfig, controllerComponents).appConfig
          .apply(FakeRequest())
      val bodyText = contentAsJson(result)
      bodyText shouldBe Json.parse(
        """
          |{
          |  "environment" : "test",
          |  "application" : "plant-simulator",
          |  "dbConfig" : {
          |    "databaseDriver" : "org.h2.Driver",
          |    "databaseUrl" : "jdbc:h2:mem:plant-simulator;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false",
          |    "databaseUser" : "***********",
          |    "databasePass" : "***********"
          |  }
          |}
      """.stripMargin
      )
    }

    Scenario("fetch the JVM metrics") {
      val result: Future[Result] =
        new ApplicationController(bindings.appConfig, controllerComponents).metrics
          .apply(FakeRequest())
      val bodyText = contentAsString(result)

      assert(bodyText.contains(""""hostname" : """))
    }
  }

  // PowerPlantDetails test
  Feature("PowerPlantController ## powerPlantDetails") {
    Scenario("fetch the details of a PowerPlant") {
      val result: Future[Result] =
        controller.powerPlantDetails(101).apply(FakeRequest())
      contentAsJson(result) shouldBe
        Json.parse("""
          |{
          |  "powerPlantId" : 101,
          |  "powerPlantName" : "joesan 1",
          |  "minPower" : 100,
          |  "maxPower" : 800,
          |  "rampPowerRate" : 20,
          |  "rampRateInSeconds" : 2,
          |  "powerPlantType" : "RampUpType"
          |}
        """.stripMargin)
    }

    Scenario("return a HTTP 404 for a non existing PowerPlant") {
      val result: Future[Result] =
        controller.powerPlantDetails(1).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      bodyText shouldBe "HTTP 404 :: PowerPlant with ID 1 not found"
    }
  }

  // Search PowerPlants test
  Feature("PowerPlantController ## searchPowerPlants") {
    val allActivePowerPlants =
      """
        |[{
        |   "powerPlantId":101,
        |   "powerPlantName":"joesan 1",
        |   "minPower":100,
        |   "maxPower":800,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |},
        |{
        |   "powerPlantId":102,
        |   "powerPlantName":"joesan 2",
        |   "minPower":200,
        |   "maxPower":1600,
        |   "powerPlantType":"OnOffType"
        |},
        |{
        |   "powerPlantId":103,
        |   "powerPlantName":"joesan 3",
        |   "minPower":300,
        |   "maxPower":2400,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |},
        |{
        |   "powerPlantId":104,
        |   "powerPlantName":"joesan 4",
        |   "minPower":400,
        |   "maxPower":3200,
        |   "powerPlantType":"OnOffType"
        |},
        |{
        |   "powerPlantId":105,
        |   "powerPlantName":"joesan 5",
        |   "minPower":500,
        |   "maxPower":4000,
        |   "rampPowerRate":20,
        |   "rampRateInSeconds":2,
        |   "powerPlantType":"RampUpType"
        |}]
      """.stripMargin

    Scenario("search all activePowerPlants") {
      val result1: Future[Result] =
        controller
          .powerPlants(onlyActive = true, page = 1)
          .apply(FakeRequest())

      val result2: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true), page = 1)
          .apply(FakeRequest())

      contentAsJson(result2) shouldBe Json.parse(allActivePowerPlants)
      contentAsJson(result1) shouldBe Json.parse(allActivePowerPlants)
    }

    Scenario("search PowerPlants only non active ones") {
      val result1: Future[Result] =
        controller
          .powerPlants(onlyActive = false, page = 1)
          .apply(FakeRequest())
      contentAsString(result1) shouldBe "[ ]"

      val result2: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(false), page = 1)
          .apply(FakeRequest())

      contentAsString(result2) shouldBe "[ ]" // All the 5 PowerPlant's in the database are active
      contentAsString(result1) shouldBe "[ ]" // All the 5 PowerPlant's in the database are active
    }

    Scenario("search all RampUpType active PowerPlant's") {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("RampUpType"))
          .apply(FakeRequest())
      contentAsJson(result) shouldBe Json.parse(
        """
          |[{
          |   "powerPlantId":101,
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |},
          |{
          |   "powerPlantId":103,
          |   "powerPlantName":"joesan 3",
          |   "minPower":300,
          |   "maxPower":2400,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |},
          |{
          |   "powerPlantId":105,
          |   "powerPlantName":"joesan 5",
          |   "minPower":500,
          |   "maxPower":4000,
          |   "rampPowerRate":20,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}]
        """.stripMargin
      )
    }

    Scenario("search all OnOffType active PowerPlant's") {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("OnOffType"))
          .apply(FakeRequest())
      contentAsJson(result) shouldBe Json.parse(
        """
          |[
          |   {
          |      "powerPlantId":102,
          |      "powerPlantName":"joesan 2",
          |      "minPower":200,
          |      "maxPower":1600,
          |      "powerPlantType":"OnOffType"
          |   },
          |   {
          |      "powerPlantId":104,
          |      "powerPlantName":"joesan 4",
          |      "minPower":400,
          |      "maxPower":3200,
          |      "powerPlantType":"OnOffType"
          |   },
          |   {
          |      "powerPlantId":106,
          |      "powerPlantName":"joesan 6",
          |      "minPower":600,
          |      "maxPower":4800,
          |      "powerPlantType":"OnOffType"
          |   }
          |]
          |
          |
        """.stripMargin
      )
    }

    Scenario("search all UnknownType active PowerPlant's") {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantType = Some("SomeUnknownType"))
          .apply(FakeRequest())
      contentAsJson(result) shouldBe Json.parse(allActivePowerPlants)
    }

    Scenario("search all active PowerPlant's with powerPlantName joesan") {
      val result: Future[Result] =
        controller
          .searchPowerPlants(onlyActive = Some(true),
                             page = 1,
                             powerPlantName = Some("joesan"))
          .apply(FakeRequest())
      contentAsJson(result) shouldBe Json.parse(allActivePowerPlants)
    }
  }

  // Create PowerPlant test
  Feature("PowerPlantController ## createPowerPlant") {
    Scenario("create a new PowerPlant successfully") {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":0,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val expected =
        """
          |{
          |   "powerPlantId":1,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) shouldBe Json.parse(expected)
    }

    Scenario(
      "not create a new PowerPlant when the PowerPlant Id is not set to 0") {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":1,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val expected =
        """{"message":"invalid PowerPlantConfig! Please set the id of the Powerplant to 0 for create new PowerPlant"}"""

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) shouldBe Json.parse(expected)
    }

    Scenario("not create a new PowerPlant for an Unknown PowerPlantType Config") {
      // When creating a new PowerPlant, we expect the id to be set to 0
      val create =
        """
          |{
          |   "powerPlantId":0,
          |   "powerPlantName":"joesan new PowerPlant",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"UnknownType"
          |}
        """.stripMargin

      val expected =
        """{"message":"Invalid PowerPlantConfig List((/powerPlantType,List(JsonValidationError(List(Invalid PowerPlantType UnknownType. Should be one of RampUpType or OnOffType),WrappedArray()))))"}""".stripMargin

      val result: Future[Result] =
        controller.createNewPowerPlant
          .apply(
            FakeRequest().withBody(Json.parse(create))
          )
      contentAsJson(result) shouldBe Json.parse(expected)
    }
  }

  // Update PowerPlant test
  Feature("PowerPlantController ## updatePowerPlant") {
    Scenario("update an active PowerPlant successfully") {
      // We are updating the PowerPlant with id = 101, We just change its name
      val jsBody =
        """
          |{
          |   "powerPlantId":101,
          |   "powerPlantName":"joesan 1 updated",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds":2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin
      val result: Future[Result] =
        controller
          .updatePowerPlant(101)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      contentAsJson(result) shouldBe Json.parse(jsBody)
    }

    Scenario("not update for an invalid PowerPlantConfig JSON") {
      // We are updating the PowerPlant with id = invalidId, Notice that the powerPlantId is invalid
      val jsBody =
        """
          |{
          |   "powerPlantId":"invalidId",
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds": 2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller
          .updatePowerPlant(101)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      result.materialize.map {
        case Success(suck) =>
          assert(suck.header.status === BAD_REQUEST)
        case Failure(_) =>
          fail(
            "Unexpected test failure when Updating a PowerPlant! Please Analyze!")
      }
    }

    Scenario("not update for a PowerPlant that does not exist in the database") {
      // We are updating the PowerPlant with id = 23001, which does not exist
      val jsBody =
        """
          |{
          |   "powerPlantId":23001,
          |   "powerPlantName":"joesan 1",
          |   "minPower":100,
          |   "maxPower":800,
          |   "rampPowerRate":20.0,
          |   "rampRateInSeconds": 2,
          |   "powerPlantType":"RampUpType"
          |}
        """.stripMargin

      val result: Future[Result] =
        controller
          .updatePowerPlant(23001)
          .apply(
            FakeRequest().withBody(Json.parse(jsBody))
          )
      result.materialize.map {
        case Success(suck) =>
          assert(suck.header.status === BAD_REQUEST)
        case Failure(_) =>
          fail(
            "Unexpected test failure when Updating a PowerPlant! Please Analyze!")
      }
    }
  }
}
