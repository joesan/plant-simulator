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

import akka.actor.{Actor, ActorLogging, Props}
import OnOffTypeActor._
import com.inland24.plantsim.core.PowerPlantEventObservable
import com.inland24.plantsim.core.SupervisorActor.TelemetrySignals
import com.inland24.plantsim.models.DispatchCommand.DispatchOnOffPowerPlant
import com.inland24.plantsim.models.PowerPlantConfig.OnOffTypeConfig
import com.inland24.plantsim.models.PowerPlantState.{ReturnToService, OutOfService}
import com.inland24.plantsim.models.ReturnToNormalCommand


class OnOffTypeActor private (config: Config)
  extends Actor with ActorLogging {

  val cfg = config.cfg
  val out = config.outChannel

  private def evolve(stm: StateMachine) = {
    val (signals, newStm) = StateMachine.popEvents(stm)
    for (s <- signals) out.onNext(s)
    context.become(active(newStm))
  }

  /*
   * Initialize the PowerPlant
   */
  override def preStart(): Unit = {
    super.preStart()
    self ! Init
  }

  override def receive: Receive = {
    case Init =>
      evolve(StateMachine.init(StateMachine.empty(cfg), cfg.minPower))
  }

  // TODO: Scaladoc!!!!
  def active(state: StateMachine): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    case DispatchOnOffPowerPlant(_,_,_,turnOn) =>
      if (turnOn)
        evolve(StateMachine.turnOn(state, maxPower = cfg.maxPower))
      else // We could also ReturnToNormal using the DispatchOnOffPowerPlant command
        evolve(StateMachine.turnOff(state, minPower = cfg.minPower))

    case ReturnToNormalCommand => // ReturnToNormal means means returning to min power
      evolve(StateMachine.turnOff(state, minPower = cfg.minPower))

    case OutOfService =>
      evolve(state.copy(signals = StateMachine.unAvailableSignals))

    case ReturnToService =>
      context.become(receive)
      self ! Init
  }
}
object OnOffTypeActor {

  case class Config(
    cfg: OnOffTypeConfig,
    outChannel: PowerPlantEventObservable
  )

  sealed trait Message
  case object Init extends Message
  case object StateRequest extends Message

  // These messages are meant for manually faulting and un-faulting the power plant
  case object OutOfService extends Message
  case object ReturnToService extends Message

  def props(cfg: Config): Props =
    Props(new OnOffTypeActor(cfg))
}