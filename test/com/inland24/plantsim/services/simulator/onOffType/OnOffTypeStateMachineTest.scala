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

import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.PowerPlantSignal.Transition
import com.inland24.plantsim.models.PowerPlantState._
import com.inland24.plantsim.models.PowerPlantType
import com.inland24.plantsim.services.simulator.onOffType.StateMachine.powerPlantIdSignalKey
import org.scalatest.WordSpecLike


class OnOffTypeStateMachineTest extends WordSpecLike {

  val onOffTpeCfg = OnOffTypeConfig(
    id = 1,
    name = "someConfig",
    minPower = 10.0,
    maxPower = 100.0,
    powerPlantType = PowerPlantType.OnOffType
  )

  // PowerPlant init tests
  "PowerPlantState # init" must {
    "initialize to a default state (available = true && onOff = false)" in {
      val initState = StateMachine.init(StateMachine.empty(onOffTpeCfg), onOffTpeCfg.minPower)
      initState.signals.foreach {
        case (key, value) if key == StateMachine.powerPlantIdSignalKey => assert(value === onOffTpeCfg.id.toString)
        case (key, value) if key == StateMachine.activePowerSignalKey  => assert(value === onOffTpeCfg.minPower.toString)
        case (key, value) if key == StateMachine.isAvailableSignalKey  => assert(value.toBoolean)
        case (key, value) if key == StateMachine.isOnOffSignalKey      => assert(!value.toBoolean) // should be Off when initializing
      }
      assert(initState.events.exists(elem => elem.isInstanceOf[Transition]))
      assert(initState.newState === Active)
      assert(initState.oldState === Init)
    }
  }

  // PowerPlant turnOn tests
  "PowerPlantState # turnOn" must {
    "turnOn when in Off state and in available state" in {
      val turnedOn = StateMachine.turnOn(
        StateMachine.init(StateMachine.empty(onOffTpeCfg), onOffTpeCfg.minPower), onOffTpeCfg.maxPower
      )
      turnedOn.signals.foreach {
        case (key, value) if key == StateMachine.powerPlantIdSignalKey => assert(value === onOffTpeCfg.id.toString)
        case (key, value) if key == StateMachine.activePowerSignalKey  => assert(value === onOffTpeCfg.maxPower.toString)
        case (key, value) if key == StateMachine.isAvailableSignalKey  => assert(value.toBoolean)
        case (key, value) if key == StateMachine.isOnOffSignalKey      => assert(value.toBoolean)
      }
      assert(turnedOn.events.exists(elem => elem.isInstanceOf[Transition]))
      assert(turnedOn.newState === Dispatched)
      assert(turnedOn.oldState === Active)
    }
  }

  // PowerPlant turnOff tests
  "PowerPlant # turnOff" must {
    "turnOff when in On state a TurnOff message is sent" in {
      // First let's turn the PowerPlant on
      val turnedOn = StateMachine.turnOn(
        StateMachine.init(StateMachine.empty(onOffTpeCfg), onOffTpeCfg.minPower), onOffTpeCfg.maxPower
      )

      // Now let's turn it off and verify the signals
      val turnedOff = StateMachine.turnOff(turnedOn, turnedOn.cfg.minPower)
      turnedOff.signals.foreach {
        case (key, value) if key == StateMachine.powerPlantIdSignalKey => assert(value === onOffTpeCfg.id.toString)
        case (key, value) if key == StateMachine.activePowerSignalKey  => assert(value === onOffTpeCfg.minPower.toString)
        case (key, value) if key == StateMachine.isAvailableSignalKey  => assert(value.toBoolean)
        case (key, value) if key == StateMachine.isOnOffSignalKey      => assert(!value.toBoolean)
      }
      assert(turnedOff.events.exists(elem => elem.isInstanceOf[Transition]))
      assert(turnedOff.newState === ReturnToNormal)
      assert(turnedOff.oldState === Dispatched)
    }
  }

  // PowerPlant OutOfService tests
  "PowerPlant # outOfService" must {
    "throw a PowerPlant to OutOfService when in active state" in {
      // First, we need a PowerPlant in Active state
      val initState = StateMachine.init(StateMachine.empty(onOffTpeCfg), onOffTpeCfg.minPower)

      // We try to send this PowerPlant to OutOfService and check the signals
      val outOfService = StateMachine.outOfService(initState)
      outOfService.signals === StateMachine.unAvailableSignals + (powerPlantIdSignalKey -> initState.cfg.id.toString)
      assert(outOfService.events.exists(elem => elem.isInstanceOf[Transition]))
      assert(outOfService.newState === OutOfService)
      assert(outOfService.oldState === Active)
    }
  }
}