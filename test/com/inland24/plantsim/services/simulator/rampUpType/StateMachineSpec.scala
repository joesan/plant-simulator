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

package com.inland24.plantsim.services.simulator.rampUpType

import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.PowerPlantSignal.{DispatchAlert, Transition}
import com.inland24.plantsim.models.PowerPlantType
import com.inland24.plantsim.models.PowerPlantState._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{FlatSpec, Matchers, WordSpecLike}

import scala.concurrent.duration._

class StateMachineSpec extends FlatSpec with WordSpecLike with Matchers {

  val Zero = 0

  val cfg = RampUpTypeConfig(
    id = 1,
    name = "RampUpType",
    minPower = 400.0,
    maxPower = 800.0,
    rampPowerRate = 100.0,
    rampRateInSeconds = 4.seconds,
    powerPlantType = PowerPlantType.RampUpType
  )

  behavior of PowerPlantState1.getClass.getCanonicalName

  // PowerPlant init tests
  "PowerPlant ## init" must {

    "start with a default state" in {
      val stm = StateMachine.init(cfg)

      assert(stm.cfg.rampPowerRate == cfg.rampPowerRate)
      assert(stm.cfg.id == cfg.id)
      assert(stm.lastRampTime.getMillis <= DateTime.now(DateTimeZone.UTC).getMillis)
      assert(stm.signals.size === 0)

      // Check the PowerPlantState
      assert(stm.newState === Init)
      assert(stm.oldState === Init)

      // There should be no Events or Alerts yet
      stm.events.size shouldBe Zero
    }

    "PowerPlantState ## init" should "initialize the default signals " +
      "(available = true, activePower = minPower, isDispatched = false)" in {
      val stm = StateMachine.init(cfg)

      assert(stm.signals.size === 3) // expecting 3 elements in the signals Map
      stm.signals.foreach {
        case (key1, value1) if key1 == PowerPlantState1.isDispatchedSignalKey => assert(!value1.toBoolean)
        case (key2, value2) if key2 == PowerPlantState1.isAvailableSignalKey  => assert(value2.toBoolean)
        case (key3, value3) if key3 == PowerPlantState1.activePowerSignalKey  => assert(value3.toDouble === cfg.minPower)
      }

      assert(stm.setPoint === cfg.minPower)
    }

    "set the PowerPlant in an active state" in {
      val stm = StateMachine.active(StateMachine.init(cfg))

      assert(stm.signals.size === 3) // expecting 3 elements in the signals Map
      stm.signals.foreach {
        case (key1, value1) if key1 == PowerPlantState1.isDispatchedSignalKey => assert(!value1.toBoolean)
        case (key2, value2) if key2 == PowerPlantState1.isAvailableSignalKey  => assert(value2.toBoolean)
        case (key3, value3) if key3 == PowerPlantState1.activePowerSignalKey  => assert(value3.toDouble === cfg.minPower)
      }
      assert(stm.setPoint === cfg.minPower)

      // Check the PowerPlantState
      stm.newState shouldBe Active
      stm.oldState shouldBe Init
    }
  }

  // PowerPlant dispatch tests
  "PowerPlant ## dispatch" must {

    // We first need an active PowerPlant
    val stm = StateMachine.active(StateMachine.init(cfg))
    val setPoint = stm.cfg.maxPower

    "not transition to RampUp state if setPoint is less than the minPower" in {
      // Let us try to dispatch
      val dispatchStm = StateMachine.dispatch(stm, stm.cfg.minPower - 10.0) // Notice, our setPoint is less than minPower

      // We expect the setPoint to be curtailed at maxPower
      dispatchStm.setPoint shouldBe stm.cfg.maxPower

      // Check the PowerPlantState (It should stay in Active)
      dispatchStm.oldState shouldBe Active
      dispatchStm.newState shouldBe Active

      // We expect one DispatchAlert event
      dispatchStm.events.size shouldBe 2

      dispatchStm.events.foreach {
        case elem if elem.isInstanceOf[DispatchAlert] =>
          elem.powerPlantConfig shouldBe stm.cfg
          elem.timeStamp should be < DateTime.now(DateTimeZone.UTC)

        case unexpected =>
          fail(s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "curtail the setPoint if the setPoint is greater than the maxPower" in {
      // Let us try to dispatch
      val dispatchStm = StateMachine.dispatch(stm, setPoint + 200.0) // Notice, our setPoint is greater than maxPower

      // We expect the setPoint to be curtailed at maxPower
      dispatchStm.setPoint shouldBe stm.cfg.maxPower

      // Check the PowerPlantState (It goes from Active to RampUp)
      dispatchStm.oldState shouldBe Active
      dispatchStm.newState shouldBe RampUp

      // We expect one Transition event and one Alert event
      dispatchStm.events.size shouldBe 2

      dispatchStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          elem.timeStamp should be < DateTime.now(DateTimeZone.UTC)

        case elem if elem.isInstanceOf[DispatchAlert] =>
          elem.powerPlantConfig shouldBe stm.cfg
          elem.timeStamp should be < DateTime.now(DateTimeZone.UTC)

        case unexpected =>
          fail(s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "use the given SetPoint and start to RampUp" in {
      // Let us try to dispatch
      val dispatchStm = StateMachine.dispatch(stm, setPoint)
      dispatchStm.setPoint shouldBe stm.cfg.maxPower

      // Check the PowerPlantState (It goes from Active to RampUp)
      dispatchStm.oldState shouldBe Active
      dispatchStm.newState shouldBe RampUp

      // We expect one Transition event
      dispatchStm.events.size shouldBe 1

      dispatchStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          elem.timeStamp should be < DateTime.now(DateTimeZone.UTC)

        case unexpected =>
          fail(s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }
  }

  // PowerPlant rampUp tests
  "PowerPlant ## rampUp" must {

    "dispatch the PowerPlant based on it's ramp rate" in {
      // We first initialize and set the StateMachine to Active
      val stm = StateMachine.active(StateMachine.init(cfg))

      /*
       * Let's dispatch this Plant to its maxPower which is 800
       * The plant is currently operating at its minPower which is 400
       * and it has a rampRate of 100 in 4 seconds, so for it to go
       * from 400 to 800, it needs in total 16 seconds
       * Let us now test if this happens!
       * The first dispatch command should take its activePower to 500
       */
      val setPoint = stm.cfg.maxPower
      val dispatchState1 = StateMachine.dispatch(
        stm.copy(setPoint = cfg.maxPower, lastRampTime = stm.lastRampTime.minusSeconds(4)), setPoint
      )
      assert(dispatchState1.signals(StateMachine.activePowerSignalKey).toDouble === 500)
      // we then come back to the current time for the lastRampTime, so that we can do the next tests
      val reset1 = dispatchState1.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      /*
       * On our second dispatch, we should go from 500 to 600, but we got to wait 4 seconds
       * Blocking may be a bad idea, so we simulate time (i.e., subtract 4 seconds to the isRampUp check)
       */
      val dispatchState2 = StateMachine.dispatch(
        reset1.copy(lastRampTime = dispatchState1.lastRampTime.minusSeconds(4)), setPoint
      )
      assert(dispatchState2.signals(StateMachine.activePowerSignalKey).toDouble === 600)
      val reset2 = dispatchState2.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      // Let's try another dispatch immediately, this should have no effect and we should still stay at 600
      val dispatchState2_copy = StateMachine.dispatch(
        reset2.copy(lastRampTime = reset2.lastRampTime.plusSeconds(1)), setPoint
      )
      assert(reset2.signals === dispatchState2_copy.signals)

      // Another 4 seconds elapse, we move to 700
      val dispatchState3 = StateMachine.dispatch(
        dispatchState2.copy(lastRampTime = dispatchState2.lastRampTime.minusSeconds(4)), setPoint
      )
      assert(dispatchState3.signals(PowerPlantState1.activePowerSignalKey).toDouble === 700)

      // Another 4 seconds elapse, we move to 800, our setPoint
      val dispatchState4 = StateMachine.dispatch(
        dispatchState3.copy(lastRampTime = dispatchState3.lastRampTime.minusSeconds(4)), setPoint
      )
      assert(dispatchState4.signals(StateMachine.activePowerSignalKey).toDouble === 800)
    }
  }

  "PowerPlantState ## returnToNormal" should "start ramping down the power plant according to its ramp rate" in {
    // The init state is a dispatched state with maxPower so that we could ReturnToNormal from that
    val dispatchedState = PowerPlantState(
      powerPlantId = 1,
      setPoint = cfg.maxPower,
      minPower = cfg.minPower,
      maxPower = cfg.maxPower,
      // Here we assume that this PowerPlant was up and running since 20 seconds
      lastRampTime = DateTime.now(DateTimeZone.UTC).minusSeconds(20),
      rampRate = cfg.rampPowerRate,
      rampRateInSeconds = cfg.rampRateInSeconds,
      signals = Map(
        activePowerSignalKey  -> cfg.maxPower.toString,
        isDispatchedSignalKey -> true.toString, // when in dispatched this is true
        isAvailableSignalKey  -> true.toString // indicates if the power plant is not available for steering
      )
    )

    /*
     * Let's ReturnToNormal this Plant which is returning to its minPower
     * The plant is currently operating at its maxPower which is 800
     * and it has a rampRate of 100 in 4 seconds, which means to go down from
     * 800 to 700 it needs 4 seconds and so on
     * Let us now test if this happens!
     * The first ReturnToNormal command should take its activePower to 700
     */
    val rtnState1 = PowerPlantState1.returnToNormal(dispatchedState)
    assert(rtnState1.signals(PowerPlantState1.activePowerSignalKey).toDouble === 700.0)
    // we now come back to the current time for the lastRampTime, so that we can do the next tests
    val reset1 = rtnState1.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

    /*
     * On our second ReturnToNormal, we should go from 700.0 to 600.0, but we got to wait 4 seconds
     * Blocking may be a bad idea, so we simulate time (i.e., subtract 4 seconds to the isRampUp check)
     */
    val rtnState2 = PowerPlantState1.returnToNormal(reset1.copy(lastRampTime = rtnState1.lastRampTime.minusSeconds(4)))
    assert(rtnState2.signals(PowerPlantState1.activePowerSignalKey).toDouble === 600.0)
    val reset2 = rtnState2.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

    // Let's try another ReturnToNormal immediately, this should have no effect and we should still stay at 600.0
    val rtnState2_copy = PowerPlantState1.returnToNormal(reset2.copy(lastRampTime = reset2.lastRampTime.plusSeconds(1)))
    assert(reset2.signals === rtnState2_copy.signals)

    // Another 4 seconds elapse, we move to 500.0
    val rtnState3 = PowerPlantState1.returnToNormal(rtnState2.copy(lastRampTime = rtnState2.lastRampTime.minusSeconds(4)))
    assert(rtnState3.signals(PowerPlantState1.activePowerSignalKey).toDouble === 500)

    // Another 4 seconds elapse, we move to 400.0, our minPower to which we ReturnToNormal to
    val rtnState4 = PowerPlantState1.returnToNormal(rtnState3.copy(lastRampTime = rtnState3.lastRampTime.minusSeconds(4)))
    assert(rtnState4.signals(PowerPlantState1.activePowerSignalKey).toDouble === cfg.minPower)
  }
}