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

import akka.actor.{Actor, ActorLogging, Props}
import RampUpTypeSimulatorActor._
import com.inland24.plantsim.core.SupervisorActor.TelemetrySignals
import com.inland24.plantsim.models.DispatchCommand.DispatchRampUpPowerPlant
import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.ReturnToNormalCommand
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.{BooleanCancelable, CompositeCancelable, SerialCancelable, SingleAssignmentCancelable}
import monix.reactive.Observable
import org.joda.time.{DateTime, DateTimeZone}
// TODO: use a passed in ExecutionContext
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Future


class RampUpTypeSimulatorActor private (cfg: RampUpTypeConfig)
  extends Actor with ActorLogging {

  val xxx= (b: Boolean) => Observable.suspend {
    if (b) Observable.raiseError(new RuntimeException("dummy"))
    else Observable.intervalAtFixedRate(cfg.rampRateInSeconds)
  }

  /*
   * Initialize the Actor instance
   */
  override def preStart(): Unit = {
    super.preStart()
    self ! Init
  }

  override def postStop(): Unit = {
    super.postStop()
    cancelSubscription("postStop()")
  }

  val subscription = SingleAssignmentCancelable()
  val source = Observable.intervalAtFixedRate(cfg.rampRateInSeconds)
  source.completed

  source.sample(cfg.rampRateInSeconds)

  private def startSubscription() = {

    def onNext(long: Long): Future[Ack] = {
      log.info(s"Doing RampCheck ${DateTime.now(DateTimeZone.UTC)}")
      self ! RampCheck
      Continue
    }
    subscription := source.subscribe(onNext _)
  }


  private def cancelSubscription(msg: String): Unit = {
    log.info(s"Cancelling subscription when $msg the PowerPlant with id = ${cfg.id}")
    subscription.cancel()
  }

  private def restartSubscription(isRestart: Boolean) = {
    Observable.suspend {
      if (isRestart)
        source
      else
        Observable.empty
    }
  }

  private def startSubscription(msg: String): Future[Unit] = Future {
    def onNext(long: Long): Future[Ack] = {
      log.info(s"Doing RampCheck ${DateTime.now(DateTimeZone.UTC)}")
      self ! RampCheck
      Continue
    }

    println(s"Setting subscription in interval ${cfg.rampRateInSeconds}")
    val obs = Observable.intervalAtFixedRate(cfg.rampRateInSeconds)
    log.info(s"Subscribed to $msg the PowerPlant with id = ${cfg.id}")
    subscription := obs.subscribe(onNext _)
  }

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

  def active(state: PowerPlantState): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // TODO: Add unit tests for the case below!
    case DispatchRampUpPowerPlant(_,_,_,dispatchPower) =>
      // If the dispatch power is equal or less than to its minPower, do nothing
      if (dispatchPower <= cfg.minPower) {
        log.info(s"Not dispatching because the current " +
          s"dispatchPower ($dispatchPower) <= minPower (${cfg.minPower}), " +
          s"so ignoring this dispatch signal for PowerPlant ${self.path.name}")
      } else {
        startSubscription("RampUp")
        val calculatedDispatch =
          if(dispatchPower >= cfg.maxPower) {
            log.warning(s"requested dispatchPower = $dispatchPower is greater " +
              s"than maxPower = ${cfg.maxPower} capacity of the PowerPlant, " +
              s"so curtailing at maxPower for PowerPlant ${self.path.name}")
            cfg.maxPower
          }
          else dispatchPower
        context.become(
          checkRamp(
            PowerPlantState.dispatch(state.copy(setPoint = calculatedDispatch))
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
    * up. The recursivity is governed by the Monix Observable!
    */
  def checkRamp(state: PowerPlantState): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    case RampCheck =>
      val isDispatched = PowerPlantState.isDispatched(state)
      // We first check if we have reached the setPoint, if yes, we switch context
      if (isDispatched) {
        // we cancel the subscription first
        cancelSubscription("RampUp")
        context.become(dispatched(state))
      } else {
        // time for another ramp up!
        context.become(
          checkRamp(PowerPlantState.dispatch(state))
        )
      }

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      // but as always, cancel the subscription first
      cancelSubscription("Dispatch")
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )
  }

  /**
    * This is the state that is transitioned when the PowerPlant
    * is fully dispatched
    */
  def dispatched(state: PowerPlantState): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      // but as always, cancel the subscription first: just in case!
      cancelSubscription("Dispatched")
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )

    case ReturnToNormalCommand(_, _) =>
      startSubscription("RampDown")
      context.become(
        checkRampDown(
          PowerPlantState.returnToNormal(state)
        )
      )
  }

  def checkRampDown(state: PowerPlantState): Receive = {
    case TelemetrySignals =>
      sender ! state.signals

    case StateRequest =>
      sender ! state

    // If we need to throw this plant OutOfService, we do it
    case OutOfService =>
      // but as always, cancel the subscription first: just in case!
      cancelSubscription("RampDown")
      context.become(
        active(state.copy(signals = PowerPlantState.unAvailableSignals))
      )

    case RampCheck =>
      println(s"checkRampDown >> is subscription cancelled ${subscription.isCanceled}")
      val isReturnedToNormal = PowerPlantState.isReturnedToNormal(state)
      println(s"checkRampDown >> is RampDown complete $isReturnedToNormal")
      // We first check if we have reached the setPoint, if yes, we switch context
      if (isReturnedToNormal) {
        // we cancel the subscription first
        cancelSubscription("RampDown")
        // and then we become active
        context.become(active(state))
      } else {
        val xxxx = PowerPlantState.returnToNormal(state)
        println(s"state now is $state")
        // time for another ramp up!
        context.become(
          checkRampDown(xxxx)
        )
      }
  }
}
object RampUpTypeSimulatorActor {

  sealed trait Message
  case object Init extends Message
  case object StateRequest extends Message
  case object Release extends Message
  case object RampCheck extends Message
  case object ReturnToNormal extends Message

  // These messages are meant for manually faulting and un-faulting the power plant
  case object OutOfService extends Message
  case object ReturnToService extends Message

  def props(cfg: RampUpTypeConfig): Props =
    Props(new RampUpTypeSimulatorActor(cfg))
}