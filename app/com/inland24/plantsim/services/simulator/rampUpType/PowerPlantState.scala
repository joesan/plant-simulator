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

package com.inland24.plantsim.services.simulator.rampUpType

import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.PowerPlantSignal
import com.inland24.plantsim.models.PowerPlantSignal.DispatchAlert
import org.joda.time.{DateTime, DateTimeZone, Seconds}

import scala.concurrent.duration._


case class PowerPlantState(
  cfg: RampUpTypeConfig,
  powerPlantId: Long,
  setPoint: Double,
  minPower: Double,
  maxPower: Double,
  lastRampTime: DateTime,
  rampRate: Double,
  rampRateInSeconds: FiniteDuration,
  events: Vector[PowerPlantSignal],
  signals: Map[String, String]
)

// TODO: refactor and rewrite
object PowerPlantState {

  def empty(id: Long, minPower: Double, maxPower: Double, rampRate: Double, rampRateInSeconds: FiniteDuration, config: RampUpTypeConfig): PowerPlantState = PowerPlantState(
    cfg = config,
    powerPlantId = id,
    setPoint = minPower,
    minPower = minPower,
    maxPower = maxPower,
    // We set the lastRampTime as the time that was now minus rampRateInSeconds
    lastRampTime = DateTime.now(DateTimeZone.UTC),
    rampRate = rampRate,
    rampRateInSeconds = rampRateInSeconds,
    events = Vector.empty[PowerPlantSignal],
    Map.empty[String, String]
  )

  val isAvailableSignalKey  = "isAvailable"
  val isDispatchedSignalKey = "isDispatched"
  val activePowerSignalKey  = "activePower"

  val unAvailableSignals = Map(
    activePowerSignalKey  -> 0.1.toString, // the power does not matter when the plant is unavailable for steering
    isDispatchedSignalKey -> false.toString,
    isAvailableSignalKey  -> false.toString // indicates if the power plant is not available for steering
  )

  def isDispatched(state: PowerPlantState): Boolean = {
    val collectedSignal = state.signals.collect { // to dispatch, you got to be available
      case (key, value) if key == activePowerSignalKey => key -> value
    }

    collectedSignal.nonEmpty && (collectedSignal(activePowerSignalKey).toDouble >= state.setPoint)
  }

  def isReturnedToNormal(state: PowerPlantState): Boolean = {
    val collectedSignal = state.signals.collect { // to ReturnToNormal, you got to be available
      case (key, value) if key == activePowerSignalKey => key -> value
    }

    collectedSignal.nonEmpty && (collectedSignal(activePowerSignalKey).toDouble <= state.minPower)
  }

  def isRampUp(timeSinceLastRamp: DateTime, rampRateInSeconds: FiniteDuration): Boolean = {
    val elapsed = Seconds.secondsBetween(DateTime.now(DateTimeZone.UTC), timeSinceLastRamp).multipliedBy(-1)
    elapsed.getSeconds.seconds >= rampRateInSeconds
  }

  def active(powerPlantState: PowerPlantState, minPower: Double): PowerPlantState = {
    powerPlantState.copy(
      signals = Map(
        activePowerSignalKey  -> minPower.toString, // be default this plant operates at min power
        isDispatchedSignalKey -> false.toString,
        isAvailableSignalKey  -> true.toString // indicates if the power plant is available for steering
      )
    )
  }

  def popEvents(state: PowerPlantState): (Seq[PowerPlantSignal], PowerPlantState) = {
    (state.events, state.copy(events = Vector.empty))
  }

  def returnToNormal(state: PowerPlantState): PowerPlantState = {
    if (isRampUp(state.lastRampTime, state.rampRateInSeconds)) {
      val collectedSignal = state.signals.collect { // to rampDown, you got to be in dispatched state
        case (key, value) if key == isDispatchedSignalKey && value.toBoolean => key -> value
      }

      val newState = if (collectedSignal.nonEmpty && state.signals.get(activePowerSignalKey).isDefined) {
        val currentActivePower = state.signals(activePowerSignalKey).toDouble
        // check if the newActivePower is lesser than the minPower
        if (currentActivePower <= state.minPower) { // if true, this means we have ramped down to the required minPower!
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> false.toString,
              activePowerSignalKey  -> state.setPoint.toString,
              isAvailableSignalKey  -> true.toString // the plant is available and not faulty!
            )
          )
        } else { // else, we do one RampDown attempt
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> true.toString,
              activePowerSignalKey  -> (currentActivePower - state.rampRate).toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
      } else state
      newState
    } else state
  }

  def dispatch(state: PowerPlantState): PowerPlantState = {

    if (isRampUp(state.lastRampTime, state.rampRateInSeconds)) {
      val collectedSignal = state.signals.collect { // to dispatch, you got to be available
        case (key, value) if key == isAvailableSignalKey && value.toBoolean => key -> value
      }

      val newState = if (collectedSignal.nonEmpty && state.signals.get(activePowerSignalKey).isDefined) {
        val currentActivePower = state.signals(activePowerSignalKey).toDouble
        // check if the newActivePower is greater than setPoint
        if (currentActivePower + state.rampRate >= state.setPoint) { // this means we have fully ramped up to the setPoint
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> true.toString,
              activePowerSignalKey  -> state.setPoint.toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
        else {
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> false.toString,
              activePowerSignalKey  -> (currentActivePower + state.rampRate).toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
      } else state
      newState
    } else state
  }
}