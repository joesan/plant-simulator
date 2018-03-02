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

import java.util.function.Consumer

import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{Helpers, TestServer, WsTestClient}

import scala.compat.java8.FutureConverters
import scala.concurrent.Await
import scala.concurrent.duration._
import com.github.andyglow.websocket._
import org.scalatest.{Matchers, WordSpecLike}
import play.api.Logger

// Test adapted from https://github.com/playframework/play-scala-websocket-example.git
class EventsWebSocketSpec extends Matchers with WordSpecLike {

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
  }
}
