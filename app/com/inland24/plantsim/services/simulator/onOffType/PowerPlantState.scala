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


case class PowerPlantState(powerPlantId: Long, signals: Map[String, String])

object PowerPlantState {

  def empty(id: Long): PowerPlantState = PowerPlantState(
    id,
    Map.empty[String, String]
  )

  val isAvailableSignalKey = "isAvailable"
  val isOnOffSignalKey = "isOnOff"
  val activePowerSignalKey = "activePower"

  val unAvailableSignals = Map(
    activePowerSignalKey -> 0.1.toString, // the power does not matter when the plant is unavailable for steering
    isOnOffSignalKey     -> false.toString,
    isAvailableSignalKey -> false.toString // indicates if the power plant is not available for steering
  )

  def init(powerPlantState: PowerPlantState, minPower: Double): PowerPlantState = {
    powerPlantState.copy(
      signals = Map(
        activePowerSignalKey -> minPower.toString, // be default this plant operates at min power
        isOnOffSignalKey     -> false.toString,
        isAvailableSignalKey -> true.toString // indicates if the power plant is available for steering
      )
    )
  }

  def turnOff(powerPlantState: PowerPlantState, minPower: Double): PowerPlantState = {
    val collectedSignals = powerPlantState.signals.collect { // to turn Off, you got to be available and be in an on state
      case (key, value) if key == isAvailableSignalKey && value.toBoolean => key -> value
      case (key, value) if key == isOnOffSignalKey && value.toBoolean     => key -> value
    }

    if (collectedSignals.size == 2) {
      powerPlantState.copy(
        signals = Map(
          activePowerSignalKey -> minPower.toString, // we turn it off to min power
          isOnOffSignalKey     -> false.toString,
          isAvailableSignalKey -> true.toString // the plant is still available and not faulty!
        )
      )
    } else {
      powerPlantState
    }
  }

  def turnOn(powerPlantState: PowerPlantState, maxPower: Double): PowerPlantState = {
    val collectedSignals = powerPlantState.signals.collect { // to turn On, you got to be available and be in an off state
      case (key, value) if key == isAvailableSignalKey && value.toBoolean => key -> value
      case (key, value) if key == isOnOffSignalKey && !value.toBoolean    => key -> value
    }

    if (collectedSignals.size == 2) {
      powerPlantState.copy(
        signals = Map(
          activePowerSignalKey -> maxPower.toString, // we turn it on to max power
          isOnOffSignalKey     -> true.toString,
          isAvailableSignalKey -> true.toString // the plant is still available and not faulty!
        )
      )
    } else {
      powerPlantState
    }
  }
}