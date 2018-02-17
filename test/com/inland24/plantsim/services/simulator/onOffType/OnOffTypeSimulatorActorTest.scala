/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inland24.plantsim.services.simulator.onOffType

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.models.DispatchCommand.DispatchOnOffPowerPlant
import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.{PowerPlantType, ReturnToNormalCommand}
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.services.simulator.onOffType.PowerPlantState._
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeActor._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

// TODO... write tests!
class OnOffTypeSimulatorActorTest extends TestKit(ActorSystem("OnOffTypeSimulatorActorTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  private val onOffTypeCfg = OnOffTypeConfig(
    id = 1,
    name = "someConfig",
    minPower = 400.0,
    maxPower = 800.0,
    powerPlantType = PowerPlantType.OnOffType
  )

  private val initPowerPlantState = PowerPlantState.init(
    PowerPlantState.empty(id = onOffTypeCfg.id),
    minPower = onOffTypeCfg.minPower
  )

  "OnOffTypeSimulatorActor" must {

    val onOffTypeSimActor = system.actorOf(OnOffTypeActor.props(onOffTypeCfg))

    // PowerPlant # Init tests
    "start with Active state" in {
      // We do this shit just so that the Actor has some time to Init
      within(1.seconds) {
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === initPowerPlantState.signals, "signals did not match")
          assert(state.powerPlantId === initPowerPlantState.powerPlantId, "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # TurnOn tests
    "turn on when a TurnOn message is sent when in Active state" in {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals(activePowerSignalKey).toDouble === onOffTypeCfg.maxPower,
            s"activePower should be ${onOffTypeCfg.maxPower} but was not"
          )
          assert(state.powerPlantId === initPowerPlantState.powerPlantId, "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # TurnOff tests
    "turn on when a TurnOn message is sent when in turned on state" in {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = false
        )
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === initPowerPlantState.signals, "signals did not match")
          assert(state.powerPlantId === initPowerPlantState.powerPlantId, "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    "turn off when a ReturnToNormalCommand message is sent when in turned on state" in {
      // First turn it on
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMsg()
      }

      // Now turin it off
      within(1.seconds) {
        onOffTypeSimActor ! ReturnToNormalCommand
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === initPowerPlantState.signals, "signals did not match")
          assert(state.powerPlantId === initPowerPlantState.powerPlantId, "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # OutOfService tests
    "go to OutOfService when OutOfService message is sent during Turned on state" in {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMsg()
      }

      within(1.seconds) {
        onOffTypeSimActor ! OutOfService
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === PowerPlantState.unAvailableSignals, "signals did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    "go to OutOfService when OutOfService message is sent during Turned off state" in {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMsg()
      }

      within(1.seconds) {
        onOffTypeSimActor ! OutOfService
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === PowerPlantState.unAvailableSignals, "signals did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # ReturnToService tests
    "return to service when ReturnToService message is sent during OutOfService state" in {
      within(1.seconds) {
        onOffTypeSimActor ! OutOfService
        expectNoMsg()
      }

      within(1.seconds) {
        onOffTypeSimActor ! ReturnToService
        expectNoMsg()
      }

      onOffTypeSimActor ! StateRequest
      expectMsgPF() {
        case state: PowerPlantState =>
          assert(state.signals === initPowerPlantState.signals, "signals did not match")
          assert(state.powerPlantId === initPowerPlantState.powerPlantId, "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }
  }
}