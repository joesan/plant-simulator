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
import com.inland24.plantsim.models.PowerPlantSignal
import org.joda.time.{DateTime, DateTimeZone, Seconds}

import scala.concurrent.duration._

case class StateMachine(
  cfg: RampUpTypeConfig,
  setPoint: Double,
  lastSetPointReceivedAt: DateTime,
  lastRampTime: DateTime,
  events: Vector[PowerPlantSignal],
  signals: Map[String, String]
)
object StateMachine {

  val isAvailableSignalKey  = "isAvailable"
  val isDispatchedSignalKey = "isDispatched"
  val activePowerSignalKey  = "activePower"

  val unAvailableSignals = Map(
    activePowerSignalKey  -> 0.1.toString, // the power does not matter when the plant is unavailable for steering
    isDispatchedSignalKey -> false.toString,
    isAvailableSignalKey  -> false.toString // indicates if the power plant is not available for steering
  )

  // The starting state of our StateMachine
  def empty(cfg: RampUpTypeConfig) = {
    StateMachine(
      cfg = cfg,
      setPoint = cfg.minPower, // By default, we start with the minimum power as our setPoint
      lastSetPointReceivedAt = DateTime.now(DateTimeZone.UTC),
      lastRampTime = DateTime.now(DateTimeZone.UTC),
      events = Vector.empty[PowerPlantSignal],
      signals = Map.empty[String, String]
    )
  }

  // Utility methods to check state transition timeouts
  def isDispatched(state: StateMachine): Boolean = {
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

  def active(stm: StateMachine, minPower: Double): StateMachine = {
    stm.copy(
      signals = Map(
        activePowerSignalKey  -> minPower.toString, // be default this plant operates at min power
        isDispatchedSignalKey -> false.toString,
        isAvailableSignalKey  -> true.toString // indicates if the power plant is available for steering
      ),
    )
  }
}