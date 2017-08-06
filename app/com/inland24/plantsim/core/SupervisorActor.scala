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

import akka.actor.{Actor, ActorKilledException, ActorLogging, ActorRef, Kill, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.SupervisorActor.Init
import com.inland24.plantsim.models.PowerPlantConfig
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig}
import com.inland24.plantsim.models.PowerPlantEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.DBServiceActor
import com.inland24.plantsim.services.database.DBServiceActor.{PowerPlantEvents, PowerPlantEventsSeq}
import com.inland24.plantsim.services.simulator.onOffType.OnOffTypeSimulatorActor
import com.inland24.plantsim.services.simulator.rampUpType.RampUpTypeSimulatorActor
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.async.Async.{async, await}
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.{Failure, Success}
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
  with ActorLogging {

  val simulatorActorNamePrefix = "plant-simulator-actor-"

  implicit val timeout = Timeout(3.seconds)

  private def fetchActor(id: Long): Future[ActorRef] = {
    context.actorSelection(s"$simulatorActorNamePrefix$id").resolveOne(2.seconds)
  }

  // Our DBServiceActor instance that is responsible for tracking changes to the PowerPlant table
  val dbServiceActor = context.actorOf(DBServiceActor.props(config.database))

  // Observable to stream events regarding PowerPlant's
  val powerPlantEventObservable =
    // For every config.database.refreshInterval in seconds
    Observable.interval(config.database.refreshInterval)
      // We ask the actor for the latest messages
      .map(_ => (dbServiceActor ? DBServiceActor.PowerPlantEvents).mapTo[PowerPlantEventsSeq])
      .concatMap(Observable.fromFuture(_))
      .concatMap(Observable.fromIterable(_))

  // Subscriber that pipes the messages to this Actor
  powerPlantEventObservable.subscribe { update =>
    (self ? update).map(_ => Continue)
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5.seconds) {
      case _: ActorKilledException =>
        SupervisorStrategy.Stop

      case e: Exception =>
        log.error("plant-simulator", e)
        SupervisorStrategy.Resume
    }

  // Our DBServiceActor reference
  //val dbActor = context.actorOf(DBServiceActor.props(config.database))

  override def preStart(): Unit = {
    super.preStart()

    context.become(active())
    self ! Init
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

  private def fetchActorRef(id: Long): Future[Option[ActorRef]] = async {
    await(fetchActor(id).materialize) match {
      case Success(actorRef) =>
        log.info(s"Fetched Actor for PowerPlant with id = $id")
        Some(actorRef)
      case Failure(fail) =>
        log.warning(s"Unable to fetch Actor for PowerPlant with id = $id because of ${fail.getCause}")
        None
    }
  }

  private def timeoutPowerPlantActor(id: Long, actorRef: ActorRef, stoppedP: Promise[Continue]) = {
    // If the Promise is not completed within 3 seconds or in other words, if we
    // try to force Kill the actor. This will trigger an ActorKilledException which
    // will subsequently result in a Terminated(actorRef) message being sent to this
    // SimulatorSupervisorActor instance
    stoppedP.future.timeout(3.seconds).recoverWith {
      case _: TimeoutException =>
        log.error(s"Time out waiting for PowerPlant actor $id to stop, so sending a Kill message")
        actorRef ! Kill
        stoppedP.future
    }
  }

  def waitForStop(stop: Promise[Continue], source: ActorRef): Receive = {
    case Continue =>
      source ! Continue
      context.become(receive)

    case Terminated(actor) =>
      context.unwatch(actor)
      stop.success(Continue)

    case someDamnThing =>
      log.error(s"Unexpected message processed $someDamnThing :: " +
        s"Expected Terminated or Continue message when terminating an actor")
  }

  def waitForStart(source: ActorRef): Receive = {
    case Continue =>
      source ! Continue
      context.become(receive)

    case someShit =>
      log.error(s"Unexpected message $someShit received while waiting for an actor to be started")
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
  override def receive: Receive = {
    /*
     * When we get a Terminated message, we remove this ActorRef from
     * the Map that we pass around!
     */
    case Terminated(actorRef) =>
      context.unwatch(actorRef)

    case PowerPlantCreateEvent(id, powerPlantCfg) =>
      log.info(s"Starting PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      // Start the PowerPlant, and pipe the message to self
      startPowerPlant(id, powerPlantCfg).pipeTo(self)
      // TODO: waitForStart not needed!
      //context.become(waitForStart(sender())) // The sender is the SimulatorSupervisorActor

    // TODO: Stop and Re-start the Actor instance and write some tests later!
    case PowerPlantUpdateEvent(id, powerPlantCfg) =>
      log.info(s"Re-starting PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      val stoppedP = Promise[Continue]()
      fetchActorRef(id)
        .map {
          case Some(actorRef) =>
            // 1. We first try to stop using context.stop
            context.stop(actorRef)
            context.watch(actorRef)
            // Let's now as a fallback, Timeout the future and force kill the Actor if needed
            timeoutPowerPlantActor(id, actorRef, stoppedP)

          case _ => // TODO: Log and shit out!
        }

    case PowerPlantDeleteEvent(id, powerPlantCfg) => // TODO
      log.info(s"Stopping PowerPlant actor with id = $id and type ${powerPlantCfg.powerPlantType}")

      val stoppedP = Promise[Continue]()
      fetchActorRef(id)
        .map {
          case Some(actorRef) =>
            // 1. We first try to stop using context.stop
            context.stop(actorRef)
            context.watch(actorRef)
            // Let's now as a fallback, Timeout the future and force kill the Actor if needed
            timeoutPowerPlantActor(id, actorRef, stoppedP)

          case _ => // TODO: Log and shit out!
        }
  }
}
object SupervisorActor {

  sealed trait Message
  case object Init extends Message

  def props(cfg: AppConfig) = Props(new SupervisorActor(cfg))
}