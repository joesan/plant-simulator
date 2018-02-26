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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.inland24.plantsim.models.DispatchCommand.DispatchRampUpPowerPlant
import com.inland24.plantsim.models.PowerPlantActorMessage._
import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.PowerPlantState.{OutOfService, ReturnToService, _}
import com.inland24.plantsim.models.PowerPlantState.ReturnToNormal
import com.inland24.plantsim.models.{PowerPlantActorMessage, ReturnToNormalCommand}
import com.inland24.plantsim.models.PowerPlantState.{Init => InitState}
import com.inland24.plantsim.services.simulator.rampUpType.RampUpTypeActor.Config
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable

import scala.concurrent.Future

// TODO: Use one supplied from outside
import monix.execution.Scheduler.Implicits.global


class RampUpTypeActor private (config: Config)
  extends Actor with ActorLogging {

  private val cfg = config.powerPlantCfg
  private val eventsStream = config.eventsStream

  /*
 * Initialize the Actor instance
 */
  override def preStart(): Unit = {
    super.preStart()
    self ! InitMessage
  }

  private def decideTransition(stm: StateMachine): Receive = stm.newState match {
    case Dispatched => dispatched(stm)
    case RampUp  => rampUp(stm, RampUpTypeActor.startRampCheckSubscription(cfg, self))
    case RampDown => rampDown(stm, RampUpTypeActor.startRampCheckSubscription(cfg, self))
    case ReturnToService | InitState => active(StateMachine.active(stm))
    case Active | ReturnToNormal | OutOfService => active(stm)
    // This should never happen, but just in case if it happens we go to the active state
    case _ => active(StateMachine.active(stm))
  }

  private def evolve(stm: StateMachine) = {
    val (signals, newStm) = StateMachine.popEvents(stm)
    for (s <- signals) {
      eventsStream.foreach(actorRef => actorRef ! s)
    }
    val receiveMethod = decideTransition(newStm)
    log.info(s"RampUpType PowerPlant with id = ${cfg.id} has " +
      s"EVOLVED STATE << " +
      s"${Some(receiveMethod.getClass.getSimpleName.split("\\$")(2)).getOrElse("unknown")} >>")
    context.become(receiveMethod)
  }

  /**
    * This is the starting point where we initialize a RampUpType PowerPlant with
    * the configuration that we get from this Actor instance. We then do a context become
    * to the active state!
    */
  override def receive: Receive = {
    case InitMessage =>
      evolve(
        StateMachine.active(
          StateMachine.init(cfg)
        )
      )
  }

  // TODO: Write Scaladoc comments
  def active(state: StateMachine): Receive = {
    case TelemetrySignalsMessage =>
      sender ! state.signals

    case PowerPlantActorMessage.StateRequestMessage =>
      sender ! state

    case DispatchRampUpPowerPlant(_,_,_,setPoint) =>
      evolve(StateMachine.dispatch(state, setPoint))
      self ! RampCheckMessage

    case OutOfServiceMessage =>
      evolve(StateMachine.outOfService(state))

    case ReturnToServiceMessage =>
      evolve(StateMachine.returnToService(state))
      self ! Init
  }

  /**
    * This state happens recursively when the PowerPlant ramps up
    * The recursivity happens until the PowerPlant is fully ramped up. The recursivity is
    * governed by the Monix Observable and its corresponding subscription
    *
    * Possible states that we can transition into are:
    *
    * 1. RampCheck - Requires a RampCheckMessage
    *    This state happens recursively and it is merely to check at regular
    *    intervals if the PowerPlant has fully ramped up to the given SetPoint.
    *    The underlying Observable subscription ensures that the RampCheck Message
    *    is sent to this actor instance at regular intervals. So for each RampCheck
    *    message that we get, we check if the PowerPlant is fully dispatched, if yes
    *    we simply cancel the underlying RampCheck Monix Observable subscription and
    *    get into a dispatched state
    * 2. OutOfService - Requires a OutOfServiceMessage
    *    This state sends the PowerPlant into out of service, meaning that this PowerPlant
    *    is not operational anymore.
    */
  def rampUp(state: StateMachine, subscription: SingleAssignmentCancelable): Receive = {
    case TelemetrySignalsMessage =>
      sender ! state.signals

    case StateRequestMessage =>
      sender ! state

    case RampCheckMessage =>
      // We first check if we have reached the setPoint, if yes, we switch context
      if (StateMachine.isDispatched(state)) {
        // Cancel the subscription first
        log.info(s"Cancelling RampUp Subscription for PowerPlant with " +
          s"Id ${state.cfg.id} because the PowerPlant is fully dispatched")
        RampUpTypeActor.cancelRampCheckSubscription(subscription)
        evolve(state)
      } else {
        // time for another ramp up!
        context.become(
          rampUp(StateMachine.rampUpCheck(state), subscription)
        )
      }

    // If we need to throw this plant OutOfService, we do it
    case OutOfServiceMessage =>
      // but as always, cancel the subscription first
      log.info(s"Cancelling RampUp Subscription for PowerPlant with Id ${state.cfg.id} " +
        s"because of PowerPlant being sent to OutOfService")
      RampUpTypeActor.cancelRampCheckSubscription(subscription)
      evolve(StateMachine.outOfService(state))
  }

  /**
    * This is the state that is transitioned when the PowerPlant
    * is fully dispatched. Possible states that we can transition into are:
    *
    * 1. OutOfService   - Throws the PowerPlant into OutOfService, scenarios where
    *                     the PowerPlant has run into some sort of error
    * 2. ReturnToNormal - When the PowerPlant has satisfied its dispatch and we want
    *                     to bring it to its active state, we use this ReturnToNormal
    *                     message
    *
    *  Additionally, messages for getting the TelemetrySignals and the StateRequest are
    *  also served by this function.
    */
  def dispatched(state: StateMachine): Receive = {
    case TelemetrySignalsMessage =>
      sender ! state.signals

    case StateRequestMessage =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfServiceMessage =>
      log.info(s"Cancelling RampUp / RampDown Subscription for PowerPlant with Id ${state.cfg.id} " +
        s"because of PowerPlant being sent to OutOfService")
      evolve(StateMachine.outOfService(state))

    case ReturnToNormalCommand(_, _) =>
      evolve(StateMachine.rampDownCheck(state))
      self ! RampCheckMessage
  }

  /**
    * This state happens recursively when the PowerPlant ramps down
    * The recursivity happens until the PowerPlant is fully ramped down. The recursivity is
    * governed by the Monix Observable and its corresponding subscription
    *
    * Possible states that we can transition into are:
    *
    * 1. RampCheck - Requires a RampCheckMessage
    *    This state happens recursively and it is merely to check at regular
    *    intervals if the PowerPlant has fully ramped down to its minPower.
    *    The underlying Observable subscription ensures that the RampCheck Message
    *    is sent to this actor instance at regular intervals. So for each RampCheck
    *    message that we get, we check if the PowerPlant is fully ReturnedToNormal, if yes
    *    we simply cancel the underlying RampCheck Monix Observable subscription and
    *    get into a dispatched state
    * 2. OutOfService - Requires a OutOfServiceMessage
    *    This state sends the PowerPlant into out of service, meaning that this PowerPlant
    *    is not operational anymore.
    */
  def rampDown(state: StateMachine, subscription: SingleAssignmentCancelable): Receive = {
    case TelemetrySignalsMessage =>
      sender ! state.signals

    case StateRequestMessage =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfServiceMessage =>
      log.info(s"Cancelling RampDown Subscription for PowerPlant with Id ${state.cfg.id} " +
        s"because of PowerPlant being sent to OutOfService")
      // but as always, cancel the subscription first: just in case!
      RampUpTypeActor.cancelRampCheckSubscription(subscription)
      evolve(StateMachine.outOfService(state))

    case RampCheckMessage =>
      // We first check if we have reached the setPoint, if yes, we switch context
      if (StateMachine.isReturnedToNormal(state)) {
        log.info(s"Cancelling RampDown Subscription for PowerPlant with Id ${state.cfg.id}")
        // we cancel the subscription first
        RampUpTypeActor.cancelRampCheckSubscription(subscription)
        // and then we become active
        evolve(state)
      } else {
        // time for another ramp down!
        context.become(
          rampDown(
            StateMachine.rampDownCheck(state),
            subscription
          )
        )
      }
  }
}
object RampUpTypeActor {

  case class Config(
    powerPlantCfg: RampUpTypeConfig,
    eventsStream: Option[ActorRef] = None
  )

  private def cancelRampCheckSubscription(subscription: SingleAssignmentCancelable): Unit = {
    subscription.cancel()
  }

  private def startRampCheckSubscription(cfg: RampUpTypeConfig, actorRef: ActorRef) = {
    val source = Observable.intervalAtFixedRate(cfg.rampRateInSeconds)

    def onNext(long: Long): Future[Ack] = {
      actorRef ! RampCheckMessage
      Continue
    }
    val subscription = SingleAssignmentCancelable()
    subscription := source.subscribe(onNext _)
    subscription
  }

  def props(cfg: Config): Props =
    Props(new RampUpTypeActor(cfg))
}