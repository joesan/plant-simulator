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

package com.inland24.plantsim.services.simulator.onOffType

import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.PowerPlantSignal.{
  DefaultAlert,
  Genesis,
  Transition
}
import com.inland24.plantsim.models.{PowerPlantSignal, PowerPlantState}
import com.inland24.plantsim.models.PowerPlantState._
import org.joda.time.{DateTime, DateTimeZone}

case class StateMachine(
    newState: PowerPlantState,
    oldState: PowerPlantState,
    lastTurnOnOffReceivedAt: DateTime,
    cfg: OnOffTypeConfig,
    signals: Map[String, String],
    events: Vector[PowerPlantSignal]
)
object StateMachine {

  // Utility method that will clear emit the events to the outside world
  def popEvents(state: StateMachine): (Seq[PowerPlantSignal], StateMachine) = {
    (state.events, state.copy(events = Vector.empty))
  }

  def empty(config: OnOffTypeConfig): StateMachine = StateMachine(
    cfg = config,
    newState = Init,
    oldState = Init,
    lastTurnOnOffReceivedAt = DateTime.now(DateTimeZone.UTC),
    signals = Map.empty[String, String],
    events = Vector(
      Genesis(
        timeStamp = DateTime.now(DateTimeZone.UTC),
        newState = Init,
        powerPlantConfig = config
      )
    )
  )

  val isAvailableSignalKey = "isAvailable"
  val isOnOffSignalKey = "isOnOff"
  val activePowerSignalKey = "activePower"
  val powerPlantIdSignalKey = "powerPlantId"

  val unAvailableSignals = Map(
    activePowerSignalKey -> 0.1.toString, // the power does not matter when the plant is unavailable for steering
    isOnOffSignalKey -> false.toString,
    isAvailableSignalKey -> false.toString // indicates if the power plant is not available for steering
  )

  def init(stm: StateMachine, minPower: Double): StateMachine = {
    stm.copy(
      oldState = stm.newState,
      newState = Active,
      signals = Map(
        powerPlantIdSignalKey -> stm.cfg.id.toString,
        activePowerSignalKey -> minPower.toString, // be default this plant operates at min power
        isOnOffSignalKey -> false.toString,
        isAvailableSignalKey -> true.toString // indicates if the power plant is available for steering
      ),
      events = stm.events :+ Transition(
        oldState = stm.newState,
        newState = Active,
        powerPlantConfig = stm.cfg,
        timeStamp = DateTime.now(DateTimeZone.UTC)
      )
    )
  }

  def outOfService(stm: StateMachine): StateMachine = {
    // If we are already in OutOfService, we don't have to do it again!
    if (stm.newState == OutOfService) {
      stm
    } else {
      stm.copy(
        oldState = stm.newState,
        newState = OutOfService,
        signals = unAvailableSignals + (powerPlantIdSignalKey -> stm.cfg.id.toString),
        events = stm.events :+ Transition(
          oldState = stm.newState,
          newState = OutOfService,
          powerPlantConfig = stm.cfg,
          timeStamp = DateTime.now(DateTimeZone.UTC)
        ) :+ DefaultAlert(
          msg = "Unexpectedly the PowerPlant is rendered OutOfService. " +
            "Please contact the PowerPlant owner @ contact@andromeda.galaxy to resolve",
          powerPlantConfig = stm.cfg,
          timeStamp = DateTime.now(DateTimeZone.UTC)
        )
      )
    }
  }

  def returnToService(stm: StateMachine): StateMachine = {
    stm.copy(
      oldState = stm.newState,
      newState = ReturnToService,
      signals = Map(
        powerPlantIdSignalKey -> stm.cfg.id.toString,
        activePowerSignalKey -> stm.cfg.minPower.toString,
        isOnOffSignalKey -> false.toString,
        isAvailableSignalKey -> true.toString
      ),
      events = stm.events :+ Transition(
        oldState = stm.newState,
        newState = ReturnToService,
        powerPlantConfig = stm.cfg,
        timeStamp = DateTime.now(DateTimeZone.UTC)
      )
    )
  }

  def turnOff(stm: StateMachine, minPower: Double): StateMachine = {
    stm.copy(
      lastTurnOnOffReceivedAt = DateTime.now(DateTimeZone.UTC),
      oldState = stm.newState,
      newState = ReturnToNormal,
      signals = Map(
        powerPlantIdSignalKey -> stm.cfg.id.toString,
        activePowerSignalKey -> minPower.toString, // we turn it off to min power
        isOnOffSignalKey -> false.toString,
        isAvailableSignalKey -> true.toString // the plant is still available and not faulty!
      ),
      events = stm.events :+ Transition(
        oldState = stm.newState,
        newState = ReturnToNormal,
        powerPlantConfig = stm.cfg,
        timeStamp = DateTime.now(DateTimeZone.UTC)
      )
    )
  }

  def turnOn(stm: StateMachine, maxPower: Double): StateMachine = {
    stm.copy(
      lastTurnOnOffReceivedAt = DateTime.now(DateTimeZone.UTC),
      oldState = stm.newState,
      newState = Dispatched,
      signals = Map(
        powerPlantIdSignalKey -> stm.cfg.id.toString,
        activePowerSignalKey -> maxPower.toString, // we turn it on to max power
        isOnOffSignalKey -> true.toString,
        isAvailableSignalKey -> true.toString // the plant is still available and not faulty!
      ),
      events = stm.events :+ Transition(
        oldState = stm.newState,
        newState = Dispatched,
        powerPlantConfig = stm.cfg,
        timeStamp = DateTime.now(DateTimeZone.UTC)
      )
    )
  }
}
