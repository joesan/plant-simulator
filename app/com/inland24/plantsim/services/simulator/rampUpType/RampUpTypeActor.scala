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
import com.inland24.plantsim.core.PowerPlantEventObservable
import com.inland24.plantsim.models.PowerPlantConfig.RampUpTypeConfig
import com.inland24.plantsim.models.PowerPlantRunState
import com.inland24.plantsim.models.PowerPlantSignal.{Genesis, Transition}
import com.inland24.plantsim.services.simulator.rampUpType.RampUpTypeActor.{Config, Init}
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.Future


class RampUpTypeActor private (config: Config)
  extends Actor with ActorLogging {

  val cfg = config.powerPlantCfg
  val out = config.outChannel

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
    // Let us signal this Init Event to the outside world
    out.onNext(
      Genesis(
        timeStamp = DateTime.now(DateTimeZone.UTC),
        newState = PowerPlantRunState.Init,
        powerPlantConfig = cfg,
      )
    )
  }

  /**
    * This is the starting point where we initialize a RampUpType PowerPlant with
    * the configuration that we get from this Actor instance. We then do a context become
    * to the active state!
    */
  override def receive: Receive = {
    case Init =>
      val powerPlantState = PowerPlantState.active(
        PowerPlantState.empty(cfg.id, cfg.minPower, cfg.maxPower, cfg.rampPowerRate, cfg.rampRateInSeconds), cfg.minPower
      )
      context.become(active(powerPlantState))
      // The PowerPlant goes to active state, we signal this to outside world
      out.onNext(
        Transition(
          timeStamp = DateTime.now(DateTimeZone.UTC),
          oldState = PowerPlantRunState.Init,
          newState = PowerPlantRunState.Active,
          powerPlantConfig = cfg
        )
      )
  }
}
object RampUpTypeActor {

  case class Config(
    powerPlantCfg: RampUpTypeConfig,
    outChannel: PowerPlantEventObservable
  )

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

  def props(cfg: Config): Props =
    Props(new RampUpTypeActor(cfg))
}