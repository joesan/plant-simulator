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

import org.joda.time.{DateTime, DateTimeZone, Seconds}

import scala.concurrent.duration._


case class PowerPlantState(
  powerPlantId: Long,
  setPoint: Double,
  lastRampTime: DateTime,
  rampRate: Double,
  rampRateInSeconds: FiniteDuration,
  signals: Map[String, String]
)

// TODO: refactor and rewrite
object PowerPlantState {

  def empty(id: Long, minPower: Double, rampRate: Double, rampRateInSeconds: FiniteDuration): PowerPlantState = PowerPlantState(
    id,
    setPoint = minPower,
    // We set the lastRampTime as the time that was now minus rampRateInSeconds
    DateTime.now(DateTimeZone.UTC),
    rampRate,
    rampRateInSeconds,
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

  def isRampUp(timeSinceLastRamp: DateTime, rampRateInSeconds: FiniteDuration): Boolean = {
    val elapsed = Seconds.secondsBetween(DateTime.now(DateTimeZone.UTC), timeSinceLastRamp).multipliedBy(-1)
    elapsed.getSeconds.seconds >= rampRateInSeconds
  }

  def init(powerPlantState: PowerPlantState, minPower: Double): PowerPlantState = {
    powerPlantState.copy(
      signals = Map(
        activePowerSignalKey  -> minPower.toString, // be default this plant operates at min power
        isDispatchedSignalKey -> false.toString,
        isAvailableSignalKey  -> true.toString // indicates if the power plant is available for steering
      )
    )
  }

  def returnToNormal(state: PowerPlantState, minPower: Double): PowerPlantState = {
    val isRampUpp = isRampUp(state.lastRampTime, state.rampRateInSeconds)
    println(isRampUpp)
    if (isRampUpp) {
      val collectedSignal = state.signals.collect { // to rampDown, you got to be in dispatched state
        case (key, value) if key == isDispatchedSignalKey && value.toBoolean => key -> value
      }

      val newState = if (collectedSignal.nonEmpty && state.signals.get(activePowerSignalKey).isDefined) {
        val currentActivePower = state.signals(activePowerSignalKey).toDouble
        // check if the newActivePower is lesser than the minPower
        if (currentActivePower <= minPower) { // if true, this means we have ramped down to the required minPower!
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> false.toString,
              activePowerSignalKey  -> state.setPoint.toString,
              isAvailableSignalKey  -> true.toString // the plant is available and not faulty!
            )
          )
        } else {
          state.copy(
            signals = Map(
              isDispatchedSignalKey -> true.toString,
              activePowerSignalKey  -> (currentActivePower - state.rampRate).toString,
              isAvailableSignalKey  -> true.toString // the plant is still available and not faulty!
            )
          )
        }
      } else { state }
      newState
    } else { state }
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
      } else { state }
      newState
    } else { state }
  }
}