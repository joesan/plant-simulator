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
import com.inland24.plantsim.models.PowerPlantActorMessage._
import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.{PowerPlantType, ReturnToNormalCommand}
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.services.simulator.onOffType.StateMachine._
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeActor._
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.featurespec.AnyFeatureSpecLike

import scala.concurrent.duration._

class OnOffTypeActorTest
    extends TestKit(ActorSystem("OnOffTypeActorTest"))
    with ImplicitSender
    with AnyFeatureSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val onOffTypeCfg = OnOffTypeConfig(
    id = 1,
    name = "someConfig",
    minPower = 400.0,
    maxPower = 800.0,
    powerPlantType = PowerPlantType.OnOffType
  )

  private val initPowerPlantState = StateMachine.init(
    StateMachine.empty(onOffTypeCfg),
    minPower = onOffTypeCfg.minPower
  )

  private val onOffActorCfg = Config(onOffTypeCfg)

  Feature("OnOffTypeActor") {

    val onOffTypeSimActor = system.actorOf(OnOffTypeActor.props(onOffActorCfg))

    // PowerPlant # Init tests
    Scenario("start with Active state") {
      // We do this shit just so that the Actor has some time to Init
      within(1.seconds) {
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(state.signals === initPowerPlantState.signals,
                 "signals did not match")
          assert(state.cfg.id === initPowerPlantState.cfg.id,
                 "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # TurnOn tests
    Scenario("turn on when a TurnOn message is sent when in Active state") {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(state
                   .signals(activePowerSignalKey)
                   .toDouble === onOffTypeCfg.maxPower,
                 s"activePower should be ${onOffTypeCfg.maxPower} but was not")
          assert(state.cfg.id === initPowerPlantState.cfg.id,
                 "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # TurnOff tests
    Scenario("turn on when a TurnOn message is sent when in turned on state") {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = false
        )
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(state.signals === initPowerPlantState.signals,
                 "signals did not match")
          assert(state.cfg.id === initPowerPlantState.cfg.id,
                 "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    Scenario(
      "turn off when a ReturnToNormalCommand message is sent when in turned on state") {
      // First turn it on
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMessage
      }

      // Now turin it off
      within(4.seconds) {
        onOffTypeSimActor ! ReturnToNormalCommand(initPowerPlantState.cfg.id)
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(state.signals === initPowerPlantState.signals,
                 "signals did not match")
          assert(state.cfg.id === initPowerPlantState.cfg.id,
                 "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # OutOfService tests
    Scenario(
      "go to OutOfService when OutOfService message is sent during Turned on state") {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMessage
      }

      within(4.seconds) {
        onOffTypeSimActor ! OutOfServiceMessage
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(
            state.signals === StateMachine.unAvailableSignals + (powerPlantIdSignalKey -> onOffTypeCfg.id.toString),
            "signals did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    Scenario(
      "go to OutOfService when OutOfService message is sent during Turned off state") {
      within(1.seconds) {
        onOffTypeSimActor ! DispatchOnOffPowerPlant(
          powerPlantId = onOffTypeCfg.id,
          command = "turnOn",
          powerPlantType = OnOffType,
          value = true
        )
        expectNoMessage
      }

      within(5.seconds) {
        onOffTypeSimActor ! OutOfServiceMessage
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(
            state.signals === StateMachine.unAvailableSignals + (powerPlantIdSignalKey -> onOffTypeCfg.id.toString),
            "signals did not match"
          )
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }

    // PowerPlant # ReturnToService tests
    Scenario(
      "return to service when ReturnToService message is sent during OutOfService state") {
      within(1.seconds) {
        onOffTypeSimActor ! OutOfServiceMessage
        expectNoMessage
      }

      within(1.seconds) {
        onOffTypeSimActor ! ReturnToServiceMessage
        expectNoMessage
      }

      onOffTypeSimActor ! StateRequestMessage
      expectMsgPF() {
        case state: StateMachine =>
          assert(state.signals === initPowerPlantState.signals,
                 "signals did not match")
          assert(state.cfg.id === initPowerPlantState.cfg.id,
                 "powerPlantId did not match")
        case x: Any => // If I get any other message, I fail
          fail(
            s"Expected a PowerPlantState as message response from the Actor, but the response was $x")
      }
    }
  }
}
