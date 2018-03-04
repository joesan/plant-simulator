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

import java.util.concurrent.{ArrayBlockingQueue, Callable}
import java.util.function.Consumer

import scala.compat.java8.FutureConverters
import scala.concurrent.duration._
import com.github.andyglow.websocket.WebsocketClient
import org.scalatestplus.play._
import play.api.test.{Helpers, TestServer, WsTestClient}
import com.inland24.plantsim.controllers.ApplicationTestFactory
import com.inland24.plantsim.services.database.DBServiceSpec
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import org.awaitility.Awaitility.{await => awaitWithTimeOut}
import play.api.http.Port
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient


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

  private implicit val httpPort: Port = new play.api.http.Port(9000)

  /**
    * Test adapted from
    * https://github.com/playframework/play-scala-websocket-example/blob/2.6.x/test/controllers/FunctionalSpec.scala
    */
  "PowerPlantOperationController" should {

    "reject a WebSocket flow if the origin is set incorrectly" in WsTestClient
      .withClient { client =>
        val app = fakeApplication
        // Pick a non standard port that will fail the (somewhat contrived) origin check...
        lazy val port: Int = 31337
        Helpers.running(TestServer(port, app)) {
          val myPublicAddress = s"localhost:$port"
          val serverURL = s"ws://$myPublicAddress/events"

          val asyncHttpClient: AsyncHttpClient =
            client.underlying[AsyncHttpClient]
          val webSocketClient = new WebSocketClient(asyncHttpClient)
          try {
            val origin = "ws://example.com/ws"
            val consumer: Consumer[String] =
              (message: String) => println(message)
            val listener = new WebSocketClient.LoggingListener(consumer)
            val completionStage =
              webSocketClient.call(serverURL, origin, listener)
            val f = FutureConverters.toScala(completionStage)
            scala.concurrent.Await.result(f, atMost = 1000.millis)
            listener.getThrowable mustBe a[IllegalStateException]
          } catch {
            case e: IllegalStateException =>
              e mustBe an[IllegalStateException]

            case e: java.util.concurrent.ExecutionException =>
              val foo = e.getCause
              foo mustBe an[IllegalStateException]
          }
        }
      }

    pending
    "accept a WebSocket flow if the origin is set correctly" in WsTestClient
      .withClient { client =>
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

        lazy val port: Int = 9000
        val app = fakeApplication
        Helpers.running(TestServer(port, app)) {
          val myPublicAddress = s"localhost:$port"
          val serverURL = s"ws://$myPublicAddress/powerPlant/events"

          val asyncHttpClient: AsyncHttpClient =
            client.underlying[AsyncHttpClient]
          val webSocketClient = new WebSocketClient(asyncHttpClient)
          val queue = new ArrayBlockingQueue[String](10)
          val origin = serverURL
          val consumer: Consumer[String] =
            (message: String) => queue.put(message)
          val listener = new WebSocketClient.LoggingListener(consumer)
          val completionStage =
            webSocketClient.call(serverURL, origin, listener)
          val f = FutureConverters.toScala(completionStage)

          // Test we can get good output from the WebSocket
          whenReady(f, timeout = Timeout(1.second)) { webSocket =>
            val condition: Callable[java.lang.Boolean] =
              () => webSocket.isOpen && queue.peek() != null
            awaitWithTimeOut().until(condition)
            val input: String = queue.take()
            val json: JsValue = Json.parse(input)
            val symbol = (json \ "symbol").as[String]
            List(symbol) must contain oneOf ("AAPL", "GOOG", "ORCL")
          }
        }
      }
  }
}
