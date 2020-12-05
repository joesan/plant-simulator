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

package com.inland24.plantsim.core

import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.test.FakeRequest
import com.inland24.plantsim.controllers.ApplicationTestFactory
import com.inland24.plantsim.services.database.DBServiceSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Port

/**
  * This test class is just there to make sure that any changes to the routes
  * file are properly tested for the correctness of the URL!
  */
class RoutesSpec
    extends PlaySpec
    with DBServiceSpec
    with BaseOneServerPerSuite
    with ApplicationTestFactory
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    System.clearProperty("ENV")
    super.h2SchemaDrop()
  }

  private implicit val httpPort: Port = new play.api.http.Port(9000)

  // Simple tests to check some Endpoints for HTTP status
  "Routes" should {

    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/kickass")).map(status) mustBe Some(
        NOT_FOUND)
    }

    "send 200 for /plantsim/config" in {
      route(app, FakeRequest(GET, "/plantsim/config")).map(status) mustBe Some(
        OK)
    }

    "send 200 for /plantsim/powerplant/metrics" in {
      route(app, FakeRequest(GET, "/plantsim/powerplant/metrics"))
        .map(status) mustBe Some(OK)
    }

    // PowerPlant add, update, read, delete Endpoints
    "send 404 for invalid PowerPlant Id in /plantsim/powerplant/:id/details" in {
      route(app, FakeRequest(GET, "/plantsim/powerplant/-1/details"))
        .map(status) mustBe Some(NOT_FOUND)
    }

    "send 200 for /plantsim/powerplants" in {
      route(app, FakeRequest(GET, "/plantsim/powerplants"))
        .map(status) mustBe Some(OK)
    }

    "send 200 for /plantsim/powerplants/search?onlyActive=true" in {
      route(app,
            FakeRequest(GET, "/plantsim/powerplants/search?onlyActive=true"))
        .map(status) mustBe Some(OK)
    }

    "send 400 for invalid PowerPlant Id in /plantsim/powerplant/:id/update" in {
      route(app, FakeRequest(POST, "/plantsim/powerplant/-1/update"))
        .map(status) mustBe Some(BAD_REQUEST)
    }

    // PowerPlant telemetry Endpoint
    "send 404 for invalid PowerPlant Id in /plantsim/powerplant/:id/telemetry" in {
      route(app, FakeRequest(POST, "/plantsim/powerplant/-1/telemetry"))
        .map(status) mustBe Some(NOT_FOUND)
    }

    "send 400 for invalid PowerPlant Id in /plantsim/powerplant/:id/dispatch" in {
      route(app, FakeRequest(POST, "/plantsim/powerplant/-1/dispatch"))
        .map(status) mustBe Some(BAD_REQUEST)
    }

    "send 400 for invalid PowerPlant Id in /plantsim/powerplant/:id/release" in {
      route(app, FakeRequest(POST, "/plantsim/powerplant/-1/release"))
        .map(status) mustBe Some(BAD_REQUEST)
    }

    // And finally our damaging Emdpoint!
    "send 404 for invalid PowerPlant Id in /plantsim/powerplant/:id/killEventStream" in {
      route(app, FakeRequest(POST, "/plantsim/powerplant/-1/killEventStream"))
        .map(status) mustBe Some(NOT_FOUND)
    }
  }
}
