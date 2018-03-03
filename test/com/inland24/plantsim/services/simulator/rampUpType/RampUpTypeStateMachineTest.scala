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
import com.inland24.plantsim.models.PowerPlantSignal.{
  DispatchAlert,
  Genesis,
  Transition
}
import com.inland24.plantsim.models.PowerPlantType
import com.inland24.plantsim.models.PowerPlantState._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest._
import Matchers._

import scala.concurrent.duration._

class RampUpTypeStateMachineTest extends WordSpecLike {

  private def now = DateTime.now(DateTimeZone.UTC)

  // TODO: This could be moved to a common place, this is duplicated in RampUpTypeActorTest as well!
  def activePowerSignalRange(power: Double) =
    power * StateMachine.toleranceFactorInPercentage / 100

  val cfg = RampUpTypeConfig(
    id = 1,
    name = "RampUpType",
    minPower = 400.0,
    maxPower = 800.0,
    rampPowerRate = 100.0,
    rampRateInSeconds = 4.seconds,
    powerPlantType = PowerPlantType.RampUpType
  )

  "StateMachine ## Utility" must {
    "generate a randomPower within a given tolerance" in {
      val activeStm = StateMachine.active(StateMachine.init(cfg))
      val newSignals = StateMachine.randomPower(activeStm.signals)

      // out activePower signal should be within the tolerance range
      val beWithinTolerance =
        be >= (cfg.minPower - activePowerSignalRange(cfg.minPower)) and be <= (cfg.minPower + activePowerSignalRange(
          cfg.minPower))

      assert(newSignals.size === 4)
      newSignals.foreach {
        case (key1, value1) if key1 == StateMachine.isDispatchedSignalKey =>
          assert(!value1.toBoolean)
        case (key2, value2) if key2 == StateMachine.isAvailableSignalKey =>
          assert(value2.toBoolean)
        case (key3, value3) if key3 == StateMachine.activePowerSignalKey =>
          value3.toDouble should beWithinTolerance
        case (key4, value4) if key4 == StateMachine.powerPlantIdSignalKey =>
          assert(value4 === cfg.id.toString)
      }
    }
  }

  // PowerPlant init tests
  "PowerPlant ## init" must {

    "start with a default state" in {
      val stm = StateMachine.init(cfg)

      assert(stm.cfg.rampPowerRate == cfg.rampPowerRate)
      assert(stm.cfg.id == cfg.id)
      assert(
        stm.lastRampTime.getMillis <= DateTime.now(DateTimeZone.UTC).getMillis)
      assert(stm.signals.size === 4)

      // Check the PowerPlantState
      assert(stm.newState === Init)
      assert(stm.oldState === Init)

      // There should just one Event, the Genesis event
      stm.events.size shouldBe 1
      stm.events.foreach {
        case elem if elem.isInstanceOf[Genesis] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "initialize the default signals " +
      "(available = true, activePower = minPower, isDispatched = false)" in {
      val stm = StateMachine.init(cfg)

      assert(stm.signals.size === 4)
      stm.signals.foreach {
        case (key1, value1) if key1 == StateMachine.isDispatchedSignalKey =>
          assert(!value1.toBoolean)
        case (key2, value2) if key2 == StateMachine.isAvailableSignalKey =>
          assert(value2.toBoolean)
        case (key3, value3) if key3 == StateMachine.activePowerSignalKey =>
          assert(value3.toDouble === cfg.minPower)
        case (key4, value4) if key4 == StateMachine.powerPlantIdSignalKey =>
          assert(value4 === cfg.id.toString)
      }
      assert(stm.setPoint === cfg.minPower)
    }

    "set the PowerPlant in an active state" in {
      val stm = StateMachine.active(StateMachine.init(cfg))

      assert(stm.signals.size === 4)
      stm.signals.foreach {
        case (key1, value1) if key1 == StateMachine.isDispatchedSignalKey =>
          assert(!value1.toBoolean)
        case (key2, value2) if key2 == StateMachine.isAvailableSignalKey =>
          assert(value2.toBoolean)
        case (key3, value3) if key3 == StateMachine.activePowerSignalKey =>
          assert(value3.toDouble === cfg.minPower)
        case (key4, value4) if key4 == StateMachine.powerPlantIdSignalKey =>
          assert(value4 === cfg.id.toString)
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
      val dispatchStm = StateMachine.dispatch(
        stm.copy(events = Vector.empty), // We clear the events that happened when active and in init
        stm.cfg.minPower - 10.0 // Notice, our setPoint is less than minPower
      )

      // The SetPoint remains at minPower
      dispatchStm.setPoint shouldBe stm.cfg.minPower

      // Check the PowerPlantState (It should stay in Active)
      dispatchStm.oldState shouldBe Init
      dispatchStm.newState shouldBe Active

      // We expect two DispatchAlert event
      dispatchStm.events.size shouldBe 1
      dispatchStm.events.foreach {
        case elem if elem.isInstanceOf[DispatchAlert] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "curtail the setPoint if the setPoint is greater than the maxPower" in {
      // Let us try to dispatch
      val dispatchStm = StateMachine.dispatch(
        stm.copy(events = Vector.empty),
        setPoint + 200.0 // Notice, our setPoint is greater than maxPower
      )

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
          assert(elem.timeStamp.isBefore(now))

        case elem if elem.isInstanceOf[DispatchAlert] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "use the given SetPoint and start to RampUp" in {
      // Let us try to dispatch
      val dispatchStm = StateMachine.dispatch(
        stm.copy(events = Vector.empty),
        setPoint
      )
      dispatchStm.setPoint shouldBe stm.cfg.maxPower

      // Check the PowerPlantState (It goes from Active to RampUp)
      dispatchStm.oldState shouldBe Active
      dispatchStm.newState shouldBe RampUp

      // We expect one Transition event
      dispatchStm.events.size shouldBe 1
      dispatchStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }
  }

  // PowerPlant out of service tests
  "PowerPlant ## outOfService" must {

    // We first need an active PowerPlant
    val stm = StateMachine.active(StateMachine.init(cfg))

    "transition to OutOfService when in Active state" in {
      val outOfServiceStm =
        StateMachine.outOfService(stm.copy(events = Vector.empty))

      // Check the PowerPlantState (It should go from Active to OutOfService)
      outOfServiceStm.oldState shouldBe Active
      outOfServiceStm.newState shouldBe OutOfService

      // We expect one Transition event
      outOfServiceStm.events.size shouldBe 1

      outOfServiceStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "transition to OutOfService when in Init state" in {
      val outOfServiceStm = StateMachine.outOfService(
        StateMachine.init(cfg).copy(events = Vector.empty)
      )

      // Check the PowerPlantState (It goes from Init to OutOfService)
      outOfServiceStm.oldState shouldBe Init
      outOfServiceStm.newState shouldBe OutOfService

      // We expect one Transition event and one Alert event
      outOfServiceStm.events.size shouldBe 1

      outOfServiceStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }

    "transition to OutOfService when in RampUp state" in {
      val rampUpStm = StateMachine.rampUpCheck(
        stm.copy(
          setPoint = stm.cfg.maxPower,
          lastRampTime = stm.lastRampTime.minusSeconds(4)
        )
      )
      val outOfServiceStm = StateMachine.outOfService(
        rampUpStm.copy(events = Vector.empty)
      )

      // Check the PowerPlantState (It goes from RampUp to OutOfService)
      outOfServiceStm.oldState shouldBe RampUp
      outOfServiceStm.newState shouldBe OutOfService

      // We expect one Transition event
      outOfServiceStm.events.size shouldBe 1

      outOfServiceStm.events.foreach {
        case elem if elem.isInstanceOf[Transition] =>
          elem.powerPlantConfig shouldBe stm.cfg
          assert(elem.timeStamp.isBefore(now) || elem.timeStamp.isEqual(now))

        case unexpected =>
          fail(
            s"Unexpected Signal $unexpected received when dispatching the PowerPlant ")
      }
    }
  }

  // PowerPlant rampUp tests
  "PowerPlant ## rampUp" must {

    val stm = StateMachine.active(StateMachine.init(cfg))
    val setPoint = stm.cfg.maxPower

    "dispatch the PowerPlant based on it's ramp rate" in {
      /*
       * Let's dispatch this Plant to its maxPower which is 800
       * The plant is currently operating at its minPower which is 400
       * and it has a rampRate of 100 in 4 seconds, so for it to go
       * from 400 to 800, it needs in total 16 seconds
       * Let us now test if this happens!
       * The first dispatch command should take its activePower to 500
       */
      val dispatchState1 = StateMachine.rampUpCheck(
        stm.copy(
          setPoint = setPoint,
          lastRampTime = stm.lastRampTime.minusSeconds(4)
        )
      )
      assert(
        dispatchState1
          .signals(StateMachine.activePowerSignalKey)
          .toDouble === 500.0)
      // we then come back to the current time for the lastRampTime, so that we can do the next tests
      val reset1 =
        dispatchState1.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      /*
       * On our second dispatch, we should go from 500 to 600, but we got to wait 4 seconds
       * Blocking may be a bad idea, so we simulate time (i.e., subtract 4 seconds to the isRampUp check)
       */
      val dispatchState2 = StateMachine.rampUpCheck(
        reset1.copy(lastRampTime = dispatchState1.lastRampTime.minusSeconds(4))
      )
      assert(
        dispatchState2
          .signals(StateMachine.activePowerSignalKey)
          .toDouble === 600.0)
      val reset2 =
        dispatchState2.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      // Let's try another dispatch immediately, this should have no effect and we should still stay at 600
      val dispatchState2_copy = StateMachine.rampUpCheck(
        reset2.copy(lastRampTime = reset2.lastRampTime.plusSeconds(1))
      )
      assert(reset2.signals === dispatchState2_copy.signals)

      // Another 4 seconds elapse, we move to 700
      val dispatchState3 = StateMachine.rampUpCheck(
        dispatchState2.copy(
          lastRampTime = dispatchState2.lastRampTime.minusSeconds(4))
      )
      assert(
        dispatchState3
          .signals(StateMachine.activePowerSignalKey)
          .toDouble === 700.0)

      // Another 4 seconds elapse, we move to 800, our setPoint
      val dispatchState4 = StateMachine.rampUpCheck(
        dispatchState3.copy(
          lastRampTime = dispatchState3.lastRampTime.minusSeconds(4))
      )
      assert(
        dispatchState4
          .signals(StateMachine.activePowerSignalKey)
          .toDouble === 800)
    }
  }

  "PowerPlant ## returnToService" must {

    // We first need an active PowerPlant
    val stm = StateMachine.active(StateMachine.init(cfg))
    val setPoint = stm.cfg.maxPower

    "go to active state from OutOfService when ReturnToService is requested" in {
      val returnToService = StateMachine.returnToService(
        StateMachine.outOfService(stm)
      )

      returnToService.oldState shouldBe OutOfService
      returnToService.newState shouldBe ReturnToService
    }
  }

  "PowerPlant ## returnToNormal" must {

    // We first need an active PowerPlant
    val stm = StateMachine.active(StateMachine.init(cfg))
    val setPoint = stm.cfg.maxPower

    "start ramping down the power plant according to its ramp rate" in {
      val dispatchedState = new StateMachine(
        oldState = RampUp,
        newState = Dispatched,
        cfg = cfg,
        setPoint = cfg.maxPower,
        // Here we assume that this PowerPlant was up and running since 20 seconds
        lastRampTime = DateTime.now(DateTimeZone.UTC).minusSeconds(20),
        events = Vector.empty,
        lastSetPointReceivedAt = DateTime.now(DateTimeZone.UTC).minusSeconds(20),
        signals = Map(
          StateMachine.activePowerSignalKey -> cfg.maxPower.toString,
          StateMachine.isDispatchedSignalKey -> true.toString, // when in dispatched this is true
          StateMachine.isAvailableSignalKey -> true.toString // indicates if the power plant is not available for steering
        )
      )

      /*
       * Let's ReturnToNormal this Plant which is returning to its minPower
       * The plant is currently operating at its maxPower which is 800
       * and it has a rampRate of 100 in 4 seconds, which means to go down from
       * 800 to 700 it needs 4 seconds and so on
       * Let us now test if this happens!
       * The first ReturnToNormal command should take its activePower from 800 to 700
       */
      val rtnState1 = StateMachine.rampDownCheck(dispatchedState)
      assert(
        rtnState1.signals(StateMachine.activePowerSignalKey).toDouble === 700.0)
      // we now come back to the current time for the lastRampTime, so that we can do the next tests
      val reset1 = rtnState1.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      /*
       * On our second ReturnToNormal, we should go from 700.0 to 600.0, but we got to wait 4 seconds
       * Blocking may be a bad idea, so we simulate time (i.e., subtract 4 seconds to the isRampUp check)
       */
      val rtnState2 = StateMachine.rampDownCheck(
        reset1.copy(lastRampTime = rtnState1.lastRampTime.minusSeconds(4)))
      assert(
        rtnState2.signals(StateMachine.activePowerSignalKey).toDouble === 600.0)
      val reset2 = rtnState2.copy(lastRampTime = DateTime.now(DateTimeZone.UTC))

      // Let's try another ReturnToNormal immediately, this should have no effect and we should still stay at 600.0
      val rtnState2_copy = StateMachine.rampDownCheck(
        reset2.copy(lastRampTime = reset2.lastRampTime.plusSeconds(1)))
      assert(reset2.signals === rtnState2_copy.signals)

      // Another 4 seconds elapse, we move to 500.0
      val rtnState3 = StateMachine.rampDownCheck(
        rtnState2.copy(lastRampTime = rtnState2.lastRampTime.minusSeconds(4)))
      assert(
        rtnState3.signals(StateMachine.activePowerSignalKey).toDouble === 500)

      // Another 4 seconds elapse, we move to 400.0, our minPower to which we ReturnToNormal to
      val rtnState4 = StateMachine.rampDownCheck(
        rtnState3.copy(lastRampTime = rtnState3.lastRampTime.minusSeconds(4)))
      assert(
        rtnState4
          .signals(StateMachine.activePowerSignalKey)
          .toDouble === cfg.minPower)
    }
  }
}
