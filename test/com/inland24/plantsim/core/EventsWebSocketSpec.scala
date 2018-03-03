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
import org.scalatest.{BeforeAndAfterAll, Ignore, WordSpecLike}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

// Test adapted from https://github.com/playframework/play-scala-websocket-example.git
//@Ignore
class EventsWebSocketSpec
    extends PlaySpec
    with WordSpecLike
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

  private implicit val httpPort = new play.api.http.Port(9000)

  "Routes" should {

    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/boum")).map(status) mustBe Some(NOT_FOUND)
    }

    "send 200 on a good request" in {
      route(app, FakeRequest(GET, "/")).map(status) mustBe Some(OK)
    }

  }
  /*
  "PowerPlantOperationController" should {
    "reject a WebSocket flow if the origin is set incorrectly" in WsTestClient.withClient { client =>

      // 1. prepare ws-client
      // 2. define message handler
      val cli = WebsocketClient[String]("ws://echo.websocket.org") {
        case str =>
          Logger.info(s"<<| $str")
      }

      // 4. open websocket
      val ws = cli.open()

      // 5. send messages
      ws ! "hello"
      ws ! "world"

      //val app = new GuiceApplicationBuilder().configure()build()

      // Pick a non standard port that will fail the (somewhat contrived) origin check...

      /*
      lazy val port: Int = 31337

      //val app = new GuiceApplicationBuilder().build()
      Helpers.running(TestServer(port)) {
        val myPublicAddress = s"localhost:$port"
        val serverURL = s"ws://$myPublicAddress/ws"

        val asyncHttpClient: AsyncHttpClient = client.underlying[AsyncHttpClient]
        val webSocketClient = new WebSocketClient(asyncHttpClient)
        try {
          val origin = "ws://example.com/ws"
          val consumer: Consumer[String] = new Consumer[String] {
            override def accept(message: String): Unit = println(message)
          }
          val listener = new WebSocketClient.LoggingListener(consumer)
          val completionStage = webSocketClient.call(serverURL, origin, listener)
          val f = FutureConverters.toScala(completionStage)
          Await.result(f, atMost = 1000.millis)
          listener.getThrowable mustBe a[IllegalStateException]
        } catch {
          case e: IllegalStateException =>
            e mustBe an [IllegalStateException]

          case e: java.util.concurrent.ExecutionException =>
            val foo = e.getCause
            foo mustBe an [IllegalStateException]
        }
      } */
    }
  } */
}
