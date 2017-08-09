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

package com.inland24.plantsim.core

import akka.actor.{Actor, ActorKilledException, ActorLogging, ActorRef, Kill, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy, Terminated}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.models.PowerPlantConfig
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig}
import com.inland24.plantsim.models.PowerPlantEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.DBServiceActor
import com.inland24.plantsim.services.database.DBServiceActor.PowerPlantEventsSeq
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeSimulatorActor
import com.inland24.plantsim.services.simulator.rampUpType.RampUpTypeSimulatorActor
import monix.execution.Ack
import monix.execution.Ack.Continue
// TODO: This import should not be here!
import monix.execution.Scheduler.Implicits.global
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

/**
  * The SupervisorActor is initialized when bootstrapping
  * the application. Have a look at [[Bootstrap]] and [[AppBindings]]
  *
  * The actor starts it's life in the init method where
  * it performs the following:
  *
  * 1. Initializes all the streams
  * 2. Attaches subscribers to the streams
  * 3. Starts the child actors and watches them
  * 4. Re-starts the child actors when needed (in case of failures)
  */
class SupervisorActor(config: AppConfig) extends Actor
  with ActorLogging with Stash {

  // We would use this to safely dispose any open connections
  val cancelable = SingleAssignmentCancelable()

  // This is how we name our actors
  val simulatorActorNamePrefix = "plant-simulator-actor-"

  // The default timeout for all Ask's the Actor makes
  implicit val timeout = Timeout(3.seconds)

  // Our DBServiceActor instance that is responsible for tracking changes to the PowerPlant table
  val dbServiceActor = context.actorOf(DBServiceActor.props(config.database))

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5.seconds) {
      case _: ActorKilledException =>
        SupervisorStrategy.Stop

      case e: Exception =>
        log.error("plant-simulator", e)
        SupervisorStrategy.Resume
    }

  override def preStart(): Unit = {
    super.preStart()

    // Observable to stream events regarding PowerPlant's
    val powerPlantEventObservable =
    // For every config.database.refreshInterval in seconds
      Observable.interval(config.database.refreshInterval)
        // We ask the actor for the latest messages
        .map(_ => (dbServiceActor ? DBServiceActor.PowerPlantEvents).mapTo[PowerPlantEventsSeq])
        .concatMap(Observable.fromFuture(_))
        .concatMap(Observable.fromIterable(_))

    // Subscriber that pipes the messages to this Actor
    cancelable := powerPlantEventObservable.subscribe { update =>
      (self ? update).map(_ => Continue)
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    cancelable.cancel()
  }

  // ***********************************************************************************
  // Methods to Start and Stop PowerPlant Actor instances
  // ***********************************************************************************
  private def startPowerPlant(id: Long, cfg: PowerPlantConfig): Future[Ack] = cfg.powerPlantType match {
    case OnOffType =>
      context.actorOf(
        OnOffTypeSimulatorActor.props(cfg.asInstanceOf[OnOffTypeConfig]),
        s"$simulatorActorNamePrefix$id"
      )
      Continue

    case RampUpType =>
      context.actorOf(
        RampUpTypeSimulatorActor.props(cfg.asInstanceOf[RampUpTypeConfig]),
        s"$simulatorActorNamePrefix$id"
      )
      Continue

    case _ => Continue
      Continue
  }

  def waitForStop(stop: Promise[Continue], source: ActorRef): Receive = {
    case Continue =>
      source ! Continue
      context.become(receive)

    case Terminated(actor) =>
      context.unwatch(actor)
      stop.success(Continue)

    case someDamnThing =>
      log.error(s"Unexpected message $someDamnThing :: " +
        s"received while waiting for an actor to be stopped")
  }

  def waitForRestart(source: ActorRef, powerPlantCreateEvent: PowerPlantCreateEvent[PowerPlantConfig]): Receive = {
    case Terminated(actor) =>
      context.unwatch(actor)
      self ! powerPlantCreateEvent
      // Now unstash all of the messages
      unstashAll()

    case someDamnThing =>
      log.error(s"Unexpected message $someDamnThing :: " +
        s"received while waiting for an actor to be stopped")
      stash()
  }

  /**
    *
    * Create Event
    * ------------
    * 1. We check if the Actor for the given PowerPlant exists
    * 2. If it exists, we forcefully kill it and spin up a new Actor instance
    *
    * Update Event
    * ------------
    * 1. Check for existence of the Actor for the given PowerPlant
    * 2. If exists, stop it - asynchronously wait for the stop
    * 3. Start a new instance of this Actor
    *
    * Delete Event
    * ------------
    * 1. PowerPlantDeleteEvent is called
    * 2. We do a context.stop
    * 3. We set a Promise
    */
  def receive: Receive = {

    case Terminated(actorRef) =>
      context.unwatch(actorRef)

    case PowerPlantCreateEvent(id, powerPlantCfg) =>
      log.info(s"Starting PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      // Start the PowerPlant, and pipe the message to self
      startPowerPlant(id, powerPlantCfg).pipeTo(self)

    case PowerPlantUpdateEvent(id, powerPlantCfg) =>
      log.info(s"Re-starting PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      context.child(s"$simulatorActorNamePrefix$id") match {
        case Some(actorRef) =>
          context.watch(actorRef)
          // We first kill the child actor instance
          actorRef ! PoisonPill

          // We wait asynchronously until this Actor is re-started
          context.become(
            waitForRestart(
              actorRef,
              PowerPlantCreateEvent(id, powerPlantCfg)
            )
          )

        case None =>
          log.warning(s"No running actor instance found for id $id :: Creating a new instance")
          self ! PowerPlantCreateEvent(id, powerPlantCfg)
      }

    case PowerPlantDeleteEvent(id, powerPlantCfg) =>
      log.info(s"Stopping PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      context.child(s"$simulatorActorNamePrefix$id") match {
        case Some(actorRef) =>
          context.watch(actorRef)
          actorRef ! Kill

        case None =>
          log.warning(s"No running actor instance found for id $id ")
      }
  }
}
object SupervisorActor {

  sealed trait Message
  case object Init extends Message

  def props(cfg: AppConfig) = Props(new SupervisorActor(cfg))
}