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
import org.scalatest.{
  BeforeAndAfterAll,
  MustMatchers,
  OptionValues,
  WordSpecLike
}
import org.scalatestplus.play.WsScalaTestClient
import play.api.mvc.Results
import monix.execution.Scheduler.Implicits.global
import monix.execution.FutureUtils.extensions._
import play.api.libs.json.Json

import scala.concurrent.Future
import play.api.mvc._
import play.api.test.Helpers.stubControllerComponents
import play.api.test._

import scala.util.{Failure, Success}

class PowerPlantOperationsControllerTest
    extends TestKit(ActorSystem("PowerPlantOperationsControllerTest"))
    with MustMatchers
    with OptionValues
    with WsScalaTestClient
    with WordSpecLike
    with Results
    with BeforeAndAfterAll
    with DBServiceSpec {

  val bindings = AppBindings.apply(system, ActorMaterializer())
  private val controllerComponents = stubControllerComponents()
  val controller =
    new PowerPlantOperationsController(bindings, controllerComponents)

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

  "PowerPlantOperationsController ## returnToNormal" should {

    "return with a HTTP NotFound for a PowerPlant that does not exist" in {
      val rtnCommand =
        """
          | {
          |   "powerPlantId" : -200
          | }
        """.stripMargin

      val result: Future[Result] =
        controller
          .returnToNormalPowerPlant(-200)
          .apply(
            FakeRequest().withBody(Json.parse(rtnCommand))
          )
      result.materialize.map {
        case Success(succ) =>
          assert(succ.header.status === NotFound)
        case Failure(ex) =>
          fail(s"Unexpected server error ${ex.getMessage}")
      }
    }

    "return with a HTTP BadRequest for an invalid JSON payload" in {
      val rtnCommand =
        """
          | {
          |   "invalid" : -200
          | }
        """.stripMargin

      val result: Future[Result] =
        controller
          .returnToNormalPowerPlant(-200)
          .apply(
            FakeRequest().withBody(Json.parse(rtnCommand))
          )
      result.materialize.map {
        case Success(succ) =>
          assert(succ.header.status === BadRequest)
        case Failure(ex) =>
          fail(s"Unexpected server error ${ex.getMessage}")
      }
    }
  }

  "PowerPlantOperationsController ## dispatchPowerPlant" should {

    "return with a HTTP NotFound for a PowerPlant that does not exist" in {
      val rtnCommand =
        """
          | {
          |   "powerPlantId" : -200
          | }
        """.stripMargin

      val result: Future[Result] =
        controller
          .returnToNormalPowerPlant(-200)
          .apply(
            FakeRequest().withBody(Json.parse(rtnCommand))
          )
      result.materialize.map {
        case Success(succ) =>
          assert(succ.header.status === NotFound)
        case Failure(ex) =>
          fail(s"Unexpected server error ${ex.getMessage}")
      }
    }

    "return with a HTTP BadRequest for an invalid JSON payload" in {
      val rtnCommand =
        """
          | {
          |   "invalid" : 2
          | }
        """.stripMargin

      val result: Future[Result] =
        controller
          .dispatchPowerPlant(2)
          .apply(
            FakeRequest().withBody(Json.parse(rtnCommand))
          )
      result.materialize.map {
        case Success(succ) =>
          assert(succ.header.status === BadRequest)
        case Failure(ex) =>
          fail(s"Unexpected server error ${ex.getMessage}")
      }
    }
  }

  "PowerPlantOperationsController ## powerPlantSignals" should {

    "return with a HTTP NotFound for a PowerPlant that does not exist" in {
      val result: Future[Result] =
        controller.powerPlantSignals(-200).apply(FakeRequest())
      result.materialize.map {
        case Success(succ) =>
          assert(succ.header.status === NotFound)
        case Failure(ex) =>
          fail(s"Unexpected server error ${ex.getMessage}")
      }
    }
  }
}
