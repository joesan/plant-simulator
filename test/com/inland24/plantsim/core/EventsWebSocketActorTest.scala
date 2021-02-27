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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.models.PowerPlantActorMessage.{
  OutOfServiceMessage,
  ReturnToServiceMessage
}
import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.services.database.DBServiceSpec
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeActor
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeActor.Config
import com.inland24.plantsim.streams.EventsStream
import monix.execution.Scheduler
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.featurespec.AnyFeatureSpecLike
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.ListBuffer

class EventsWebSocketActorTest
    extends TestKit(ActorSystem("EventsWebSocketActorTest"))
    with ImplicitSender
    with AnyFeatureSpecLike
    with Matchers
    with BeforeAndAfterAll
    with DBServiceSpec {

  override def beforeAll: Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll: Unit = {
    System.clearProperty("ENV")
    super.h2SchemaDrop()
    TestKit.shutdownActorSystem(system)
  }

  // Use a test AppConfig
  // (We test against application.test.conf - See DBServiceSpec) where we
  // set this as Environment variable
  private implicit val ec: Scheduler =
    monix.execution.Scheduler.Implicits.global

  // This will be our PowerPlantActor instance
  private val onOffTypeCfg: OnOffTypeConfig = OnOffTypeConfig(
    102,
    "joesan 102",
    200.0,
    1600.0,
    OnOffType
  )

  Feature("EventsWebSocketActor # telemetrySignals") {

    val powerPlantObservable = PowerPlantEventObservable(ec)

    // This will be the channel which our PowerPlantActor will use to push messages
    val publishChannel: ActorRef =
      system.actorOf(EventsStream.props(powerPlantObservable))
    val powerPlantActor: ActorRef = system.actorOf(
      OnOffTypeActor.props(Config(onOffTypeCfg, Some(publishChannel)))
    )

    // This is our buffer to which we can save and check the test expectations
    val buffer: ListBuffer[String] = ListBuffer.empty[String]

    // This will be our sink to which the publishChannel will pipe messages to the WebSocket endpoint
    class SinkActor extends Actor {
      override def receive: Receive = {
        case jsonStr: String =>
          println(s"obtained String is $jsonStr")
          buffer += jsonStr
      }
    }
    val sink = system.actorOf(Props(new SinkActor))

    Scenario("produce telemetry signals") {
      // Reset the buffer
      buffer.clear()

      // Let us create our EventsWebSocketActor instance (for TelemetrySignals)
      system.actorOf(
        EventsWebSocketActor.props(
          EventsWebSocketActor.telemetrySignals(102, powerPlantActor),
          sink
        )
      )

      /* Our EventsWebSocketActor pushes TelemetrySignals every 5 seconds, so to
       * collect some signals in our sink Actor, let us wait for 10 seconds
       * Unfortunately, I have to block! Any better ideas????
       */
      Thread.sleep(10000)

      val expected =
        Json
          .parse(
            """{"powerPlantId":"102","activePower":200.0,"isOnOff":false,"isAvailable":true}""")
          .as[Map[String, JsValue]]
      // Let us check our expectations (We expect a total of 2 Signals)
      assert(buffer.size >= 2)
      val first = Json.parse(buffer.head).as[Map[String, JsValue]]
      assert(first("powerPlantId") === expected("powerPlantId"))

      val last = Json.parse(buffer.last).as[Map[String, JsValue]]
      assert(last("powerPlantId") === expected("powerPlantId"))
    }
  }

  Feature("EventsWebSocketActor # Events and Alerts") {

    val powerPlantObservable = PowerPlantEventObservable(ec)

    // This will be the channel which our PowerPlantActor will use to push messages
    val publishChannel: ActorRef =
      system.actorOf(EventsStream.props(powerPlantObservable))
    val powerPlantActor: ActorRef = system.actorOf(
      OnOffTypeActor.props(Config(onOffTypeCfg, Some(publishChannel)))
    )

    // This is our buffer to which we can save and check the test expectations
    val buffer: ListBuffer[String] = ListBuffer.empty[String]

    // This will be our sink to which the publishChannel will pipe messages to the WebSocket endpoint
    class SinkActor extends Actor {
      override def receive: Receive = {
        case jsonStr: String =>
          buffer += jsonStr
      }
    }
    val sink = system.actorOf(Props(new SinkActor))

    Scenario("produce events and alerts") {
      // Reset the buffer
      buffer.clear()

      // Let us create our EventsWebSocketActor instance (for Events)
      system.actorOf(
        EventsWebSocketActor.props(
          EventsWebSocketActor.eventsAndAlerts(Some(102), powerPlantObservable),
          sink
        )
      )
      // We unfortunately do this shitty sleep, I have no other ideas to make this better!
      Thread.sleep(1000)

      // To be able to receive Events, let us send some message to the PowerPlantActor
      powerPlantActor ! OutOfServiceMessage
      Thread.sleep(1000)

      powerPlantActor ! ReturnToServiceMessage
      Thread.sleep(1000)

      // We expect 2 events (OutOfService, ReturnToService) and one Alert
      assert(buffer.size === 3)
      val headJson = Json.parse(buffer.head)
      assert((headJson \ "newState").as[String] === "OutOfService")
      assert((headJson \ "oldState").as[String] === "Active")
      assert((headJson \ "powerPlantCfg" \ "powerPlantId").as[Int] === 102)

      val lastJson = Json.parse(buffer.last)
      assert((lastJson \ "newState").as[String] === "ReturnToNormal")
      assert((lastJson \ "oldState").as[String] === "OutOfService")
      assert((lastJson \ "powerPlantCfg" \ "powerPlantId").as[Int] === 102)
    }
  }
}
