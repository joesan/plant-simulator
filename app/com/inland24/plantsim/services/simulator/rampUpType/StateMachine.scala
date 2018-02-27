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
import com.inland24.plantsim.models.{PowerPlantSignal, PowerPlantState}
import com.inland24.plantsim.models.PowerPlantSignal.{DispatchAlert, Genesis, Transition}
import com.inland24.plantsim.models.PowerPlantState._
import org.joda.time.{DateTime, DateTimeZone, Seconds}

import scala.concurrent.duration._


case class StateMachine(
  cfg: RampUpTypeConfig,
  setPoint: Double,
  lastSetPointReceivedAt: DateTime,
  lastRampTime: DateTime,
  oldState: PowerPlantState,
  newState: PowerPlantState,
  events: Vector[PowerPlantSignal],
  signals: Map[String, String]
) {
  override def toString: String = {
    s"""
       |id = ${cfg.id}, setPoint = $setPoint, lastSetPointReceivedAt = $lastSetPointReceivedAt, lastRampTime = $lastRampTime
       |oldState = $oldState, newState = $newState
       |events = $events.mkString(",")
       |signals = $signals.mkString(",")
    """.stripMargin
  }
}
object StateMachine {

  /* This factor determines the randomness for the activePower
   For example., if the power to be dispatched is 800, then the
   with the toleranceFactor of 2% would mean that the
   activePower for the PowerPlant in dispatched state would vary
   between 800 * 2 / 100 = 16
   So the activePower would vary between 784 and 816
   This factor is just introduced to show some randomness.
   For simplicity, we hardcode this value here for all RampUpType
   PowerPlants to have the same toleranceFactor. Ideally, each
   RampUpType PowerPlant should have its own toleranceFactor configured
   in the database!
 */
  private val toleranceFactorInPercentage = 2

  val isAvailableSignalKey   = "isAvailable"
  val isDispatchedSignalKey  = "isDispatched"
  val activePowerSignalKey   = "activePower"
  val powerPlantIdSignalKey  = "powerPlantId"

  val unAvailableSignals = Map(
    activePowerSignalKey  -> 0.1.toString, // the power does not matter when the plant is unavailable for steering
    isDispatchedSignalKey -> false.toString,
    isAvailableSignalKey  -> false.toString // indicates if the power plant is not available for steering
  )

  // Utility method that will clear and emit the events to the outside world
  def popEvents(state: StateMachine): (Seq[PowerPlantSignal], StateMachine) = {
    (state.events, state.copy(events = Vector.empty))
  }

  def randomPower(signals: Map[String, String]): Map[String, String] =  {

    def random(power: Double) = {
      val range = power * toleranceFactorInPercentage / 100
      val rnd = new scala.util.Random
      val lower = power - range
      val upper = power + range
      lower + rnd.nextInt( (upper.toInt - lower.toInt) + 1 )
    }

    val signalsNew = signals.map {
      case (key, value) if key == activePowerSignalKey => key -> random(value.toDouble).toString
    }
    println(s"newSignals ************ is $signalsNew")
    signalsNew
  }

  // Utility methods to check state transition timeouts
  def isDispatched(state: StateMachine): Boolean = {
    val collectedSignal = state.signals.collect { // to dispatch, you got to be available
      case (key, value) if key == activePowerSignalKey => key -> value
    }

    collectedSignal.nonEmpty && (collectedSignal(activePowerSignalKey).toDouble >= state.setPoint)
  }

  def isReturnedToNormal(state: StateMachine): Boolean = {
    val collectedSignal = state.signals.collect { // to ReturnToNormal, you got to be available
      case (key, value) if key == activePowerSignalKey => key -> value
    }

    collectedSignal.nonEmpty && (collectedSignal(activePowerSignalKey).toDouble <= state.cfg.minPower)
  }

  def isTimeForRamp(timeSinceLastRamp: DateTime, rampRateInSeconds: FiniteDuration): Boolean = {
    val elapsed = Seconds.secondsBetween(DateTime.now(DateTimeZone.UTC), timeSinceLastRamp).multipliedBy(-1)
    elapsed.getSeconds.seconds >= rampRateInSeconds
  }

  // The starting state of our StateMachine
  def init(cfg: RampUpTypeConfig) = {
    StateMachine(
      cfg = cfg,
      oldState = Init,
      newState = Init,
      setPoint = cfg.minPower, // By default, we start with the minimum power as our setPoint
      lastSetPointReceivedAt = DateTime.now(DateTimeZone.UTC),
      lastRampTime = DateTime.now(DateTimeZone.UTC),
      events = Vector(
        Genesis(
          timeStamp = DateTime.now(DateTimeZone.UTC),
          newState = Init,
          powerPlantConfig = cfg
        )
      ),
      signals = Map(
        powerPlantIdSignalKey -> cfg.id.toString,
        activePowerSignalKey  -> cfg.minPower.toString, // by default this plant operates at min power
        isDispatchedSignalKey -> false.toString,
        isAvailableSignalKey  -> true.toString // indicates if the power plant is available for steering
      )
    )
  }

  // State transition methods
  def active(stm: StateMachine): StateMachine = {
    stm.copy(
      newState = com.inland24.plantsim.models.PowerPlantState.Active,
      oldState = stm.newState,
      signals = Map(
        powerPlantIdSignalKey -> stm.cfg.id.toString,
        activePowerSignalKey  -> stm.cfg.minPower.toString, // be default this plant operates at min power
        isDispatchedSignalKey -> false.toString,
        isAvailableSignalKey  -> true.toString // indicates if the power plant is available for steering
      ),
      events = stm.events :+ Transition(
          newState = com.inland24.plantsim.models.PowerPlantState.Active,
          oldState = stm.newState,
          powerPlantConfig = stm.cfg
        )
    )
  }

  def outOfService(stm: StateMachine): StateMachine = {
    stm.copy(
      newState = OutOfService,
      oldState = stm.newState,
      signals = unAvailableSignals + (powerPlantIdSignalKey -> stm.cfg.id.toString),
      events = stm.events :+ Transition(
          newState = OutOfService,
          oldState = stm.newState,
          powerPlantConfig = stm.cfg,
          timeStamp = DateTime.now(DateTimeZone.UTC)
        )
    )
  }

  def returnToService(stm: StateMachine): StateMachine = {
    stm.copy(
      setPoint = stm.cfg.minPower,
      newState = ReturnToService,
      oldState = stm.newState,
      signals = unAvailableSignals + (powerPlantIdSignalKey -> stm.cfg.id.toString),
      events = stm.events :+ Transition(
          newState = ReturnToService,
          oldState = stm.newState,
          powerPlantConfig = stm.cfg
        )
    )
  }

  def dispatch(stm: StateMachine, setPoint: Double): StateMachine = {
    // 1. Check if the setPoint or the power to be dispatched is greater than the minPower
    if (setPoint <= stm.cfg.minPower) {
      stm.copy(
        events = Vector(
          DispatchAlert(
            msg = s"requested dispatchPower = $setPoint is lesser than " +
              s"minPower = ${stm.cfg.minPower} capacity of the PowerPlant, " +
              s"so curtailing at maxPower for PowerPlant ${stm.cfg.id}",
            powerPlantConfig = stm.cfg,
            timeStamp = DateTime.now(DateTimeZone.UTC)
          )
        ) ++ stm.events
      )
    } else if (setPoint > stm.cfg.maxPower) { // If SetPoint greater than maxPower, curtail it
      stm.copy(
        setPoint = stm.cfg.maxPower, // curtailing the SetPoint to maxPower
        lastSetPointReceivedAt = DateTime.now(DateTimeZone.UTC),
        oldState = stm.newState,
        newState = RampUp,
        events = stm.events :+ Transition(
            oldState = stm.newState,
            newState = RampUp,
            powerPlantConfig = stm.cfg
          ) :+
          DispatchAlert(
            s"requested dispatchPower = $setPoint is greater than " +
              s"maxPower = ${stm.cfg.maxPower} capacity of the PowerPlant, " +
              s"so curtailing at maxPower for PowerPlant ${stm.cfg.id}",
            stm.cfg
          )
      )
    } else {
      stm.copy(
        setPoint = setPoint,
        lastSetPointReceivedAt = DateTime.now(DateTimeZone.UTC),
        oldState = stm.newState,
        newState = RampUp,
        events = stm.events :+ Transition(
            oldState = stm.newState,
            newState = RampUp,
            powerPlantConfig = stm.cfg
          )
      )
    }
  }

  def rampDownCheck(state: StateMachine): StateMachine = { // ReturnToNormal means RampDown
    if (isTimeForRamp(state.lastRampTime, state.cfg.rampRateInSeconds)) {
      val collectedSignal = state.signals.collect { // to rampDown, you got to be in dispatched state
        case (key, value) if key == isDispatchedSignalKey && value.toBoolean => key -> value
      }

      if (collectedSignal.nonEmpty && state.signals.get(activePowerSignalKey).isDefined) {
        val currentActivePower = state.signals(activePowerSignalKey).toDouble
        // check if the newActivePower is lesser than the minPower
        if (currentActivePower - state.cfg.rampPowerRate <= state.cfg.minPower) { // if true, this means we have ramped down to the required minPower!
          state.copy(
            oldState = state.newState,
            newState = Active,
            events = state.events :+ Transition(
                newState = Active,
                oldState = state.newState,
                powerPlantConfig = state.cfg
              ),
            signals = Map(
              powerPlantIdSignalKey -> state.cfg.id.toString,
              isDispatchedSignalKey -> false.toString,
              activePowerSignalKey  -> state.cfg.minPower.toString,
              isAvailableSignalKey  -> true.toString // the plant is available and not faulty!
            )
          )
        } else { // else, we do one RampDown attempt
          state.copy(
            lastRampTime = DateTime.now(DateTimeZone.UTC),
            newState = RampDown,
            oldState = state.newState,
            signals = Map(
              powerPlantIdSignalKey -> state.cfg.id.toString,
              isDispatchedSignalKey -> true.toString,
              activePowerSignalKey  -> (currentActivePower - state.cfg.rampPowerRate).toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
      } else state
    } else state
  }

  def rampUpCheck(stm: StateMachine): StateMachine = {
    if (isTimeForRamp(stm.lastRampTime, stm.cfg.rampRateInSeconds)) {
      val collectedSignal = stm.signals.collect { // to dispatch, you got to be available
        case (key, value) if key == isAvailableSignalKey && value.toBoolean => key -> value
      }

      val newState = if (collectedSignal.nonEmpty && stm.signals.get(activePowerSignalKey).isDefined) {
        val currentActivePower = stm.signals(activePowerSignalKey).toDouble
        // check if the newActivePower is greater than setPoint
        if (currentActivePower + stm.cfg.rampPowerRate >= stm.setPoint) { // This means we have fully ramped up to the setPoint
          stm.copy(
            newState = Dispatched,
            oldState = stm.newState,
            signals = Map(
              powerPlantIdSignalKey -> stm.cfg.id.toString,
              isDispatchedSignalKey -> true.toString,
              activePowerSignalKey  -> stm.setPoint.toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            ),
            events = stm.events :+ Transition(
                oldState = stm.newState,
                newState = Dispatched,
                powerPlantConfig = stm.cfg
              )
          )
        }
        else { // We still have to RampUp
          stm.copy(
            lastRampTime = DateTime.now(DateTimeZone.UTC),
            newState = RampUp,
            oldState = stm.newState,
            signals = Map(
              powerPlantIdSignalKey -> stm.cfg.id.toString,
              isDispatchedSignalKey -> false.toString,
              activePowerSignalKey  -> (currentActivePower + stm.cfg.rampPowerRate).toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
      } else stm
      newState
    } else stm
  }
}