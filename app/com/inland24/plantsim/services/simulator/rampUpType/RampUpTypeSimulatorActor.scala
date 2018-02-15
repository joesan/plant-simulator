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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import RampUpTypeSimulatorActor._
import com.inland24.plantsim.core.SupervisorActor.TelemetrySignals
import com.inland24.plantsim.models.DispatchCommand.DispatchRampUpPowerPlant
import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.ReturnToNormalCommand
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable
// TODO: use a passed in ExecutionContext
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Future

/**
  * This Actor is responsible for the operations of a RampUpType PowerPlant
  * [[com.inland24.plantsim.models.PowerPlantType.RampUpType]]
  * @param cfg
  */
class RampUpTypeSimulatorActor private (cfg: RampUpTypeConfig)
  extends Actor with ActorLogging {

  /* This factor determines the randomness for the activePower
     For example., if the power to be dispatched is 800, then the
     with the toleranceFactor of 2% would mean that the
     activePower for the PowerPlant in dispatched state would vary
     between 800 * 2 / 100 = plus or minus 16
     So the activePower would vary between 784 and 816
     This factor is just introduced to show some randomness
     For simplicity, we hardcode this value here for all RampUpType
     PowerPlants to have the same toleranceFactor. Ideally, each
     RampUpType PowerPlant should have its own toleranceFactor configured
     in the database!
   */
  private val toleranceFactorInPercentage = 2

  /*
   * Initialize the Actor instance
   */
  override def preStart(): Unit = {
    super.preStart()
    self ! Init
  }

  /**
    * This is the starting point where we initialize a RampUpType PowerPlant with
    * the configuration that we get from this Actor instance. We then do a context become
    * to the active state!
    */
  override def receive: Receive = {
    case Init =>
      context.become(
        active(
          PowerPlantState.init(
            PowerPlantState.empty(cfg.id, cfg.minPower, cfg.maxPower, cfg.rampPowerRate, cfg.rampRateInSeconds), cfg.minPower
          )
        )
      )
  }

  /**
    * This is the state a normal operational RampUpType PowerPlant should spend most of
    * its time. This Actor responds to several messages from the outside world. For example.,
    * when the external world sends a [[com.inland24.plantsim.core.SupervisorActor.TelemetrySignals]],
    * the actor responds by fetching the signals from the current state
    *
    * @param state
    * @return
    */
  def active(state: PowerPlantState): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // TODO: Add unit tests for the case below!
    case DispatchRampUpPowerPlant(_,_,_,dispatchPower) =>
      // If the dispatch power is equal or less than the minPower, do nothing
      if (dispatchPower <= cfg.minPower) {
        log.info(s"Not dispatching because the current " +
          s"dispatchPower ($dispatchPower) <= minPower (${cfg.minPower}), " +
          s"so ignoring this dispatch signal for PowerPlant ${self.path.name}")
      } else {
        val calculatedDispatch =
          if(dispatchPower >= cfg.maxPower) {
            log.warning(s"requested dispatchPower = $dispatchPower is greater " +
              s"than maxPower = ${cfg.maxPower} capacity of the PowerPlant, " +
              s"so curtailing at maxPower for PowerPlant ${self.path.name}")
            cfg.maxPower
          }
          else dispatchPower
        log.info(s"Starting Observable sequence for doing RampUpCheck for PowerPlant ${cfg.id}")
        context.become(
          checkRamp(
            PowerPlantState.dispatch(state.copy(setPoint = calculatedDispatch)),
            RampUpTypeSimulatorActor.startRampCheckSubscription(cfg, self)
          )
        )
      }

    case OutOfService =>
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )

    case ReturnToService =>
      context.become(receive)
      self ! Init
  }

  /**
    * This state happens recursively when the PowerPlant ramps up
    * The recursivity happens until the PowerPlant is fully ramped
    * up. The recursivity is governed by the Monix Observable and its
    * corresponding subscription
    */
  def checkRamp(state: PowerPlantState, subscription: SingleAssignmentCancelable): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    case RampCheck =>
      val isDispatched = PowerPlantState.isDispatched(state)
      // We first check if we have reached the setPoint, if yes, we switch context
      if (isDispatched) {
        // Cancel the subscription first
        log.info(s"Cancelling RampUp Subscription for PowerPlant with Id ${state.powerPlantId}")
        RampUpTypeSimulatorActor.cancelRampCheckSubscription(subscription)

        /*
         We pass the subscription to the dispatched state, just in case if something
         went wrong and our RampUpCheck subscriber was still active. Calling cancel
         on an already cancelled Subscription does not have any side effects as such,
         so it is safe to pass the subscription around and cancel it on a needed basis
        */
        context.become(dispatched(state, subscription))
      } else {
        // time for another ramp up!
        context.become(
          checkRamp(PowerPlantState.dispatch(state), subscription)
        )
      }

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      // but as always, cancel the subscription first
      log.info(s"Cancelling RampUp Subscription for PowerPlant with Id ${state.powerPlantId} " +
        s"because of PowerPlant being sent to OutOfService")
      RampUpTypeSimulatorActor.cancelRampCheckSubscription(subscription)
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )
  }

  /**
    * This is the state that is transitioned when the PowerPlant
    * is fully dispatched
    */
  def dispatched(state: PowerPlantState, subscription: SingleAssignmentCancelable): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      // but as always, cancel the subscription first: just in case!
      log.info(s"Cancelling RampUp / RampDown Subscription for PowerPlant with Id ${state.powerPlantId} " +
        s"because of PowerPlant being sent to OutOfService")
      RampUpTypeSimulatorActor.cancelRampCheckSubscription(subscription)
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )

    case ReturnToNormalCommand(_, _) =>
      context.become(
        checkRampDown(
          PowerPlantState.returnToNormal(state),
          RampUpTypeSimulatorActor.startRampCheckSubscription(cfg, self)
        )
      )
  }

  // TODO: add comments!
  def checkRampDown(state: PowerPlantState, subscription: SingleAssignmentCancelable): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      log.info(s"Cancelling RampDown Subscription for PowerPlant with Id ${state.powerPlantId} " +
        s"because of PowerPlant being sent to OutOfService")
      // but as always, cancel the subscription first: just in case!
      RampUpTypeSimulatorActor.cancelRampCheckSubscription(subscription)
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )

    case RampCheck =>
      val isReturnedToNormal = PowerPlantState.isReturnedToNormal(state)
      // We first check if we have reached the setPoint, if yes, we switch context
      if (isReturnedToNormal) {
        log.info(s"Cancelling RampDown Subscription for PowerPlant with Id ${state.powerPlantId}")
        // we cancel the subscription first
        RampUpTypeSimulatorActor.cancelRampCheckSubscription(subscription)
        // and then we become active
        context.become(active(state))
      } else {
        // time for another ramp up!
        context.become(
          checkRampDown(
            PowerPlantState.returnToNormal(state),
            subscription
          )
        )
      }
  }
}
object RampUpTypeSimulatorActor {

  case class Config()

  sealed trait Message
  case object Init extends Message
  case object StateRequest extends Message
  case object Release extends Message
  case object RampCheck extends Message
  case object ReturnToNormal extends Message

  // These messages are meant for manually faulting and un-faulting the power plant
  case object OutOfService extends Message
  case object ReturnToService extends Message

  private def cancelRampCheckSubscription(subscription: SingleAssignmentCancelable): Unit = {
    subscription.cancel()
  }

  private def startRampCheckSubscription(cfg: RampUpTypeConfig, actorRef: ActorRef) = {
    val source = Observable.intervalAtFixedRate(cfg.rampRateInSeconds)

    def onNext(long: Long): Future[Ack] = {
      actorRef ! RampCheck
      Continue
    }
    val subscription = SingleAssignmentCancelable()
    subscription := source.subscribe(onNext _)
    subscription
  }

  def props(cfg: RampUpTypeConfig): Props =
    Props(new RampUpTypeSimulatorActor(cfg))
}