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

package com.inland24.plantsim.services.database

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.inland24.plantsim.config.DBConfig
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.models.PowerPlantConfig.PowerPlantsConfig
import com.inland24.plantsim.models.PowerPlantDBEvent.{
  PowerPlantCreateEvent,
  PowerPlantDeleteEvent,
  PowerPlantUpdateEvent
}
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantDBEvent}
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.repository.impl.PowerPlantRepoAsTask
import com.inland24.plantsim.streams.DBObservable
// ******* Note: Both these imports should be here! Do not remove them!
import monix.cats._
import monix.eval.Task
// *******
import monix.execution.{Ack, Scheduler}
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.observers.Subscriber
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.Future

/**
  * This Actor is responsible for reacting to updates on a PowerPlant
  * in the database. So whenever a PowerPlant is updated, the update
  * is pushed into this actor via the underlying [[DBObservable]]
  * and this update is then interpreted accordingly if it is a create
  * update or a delete of a PowerPlant. The subsequent events are then
  * emitted when asked for the events.
  *
  * TODO: If the database is down the stream should not throw an error!!!
  * TODO: but rather it should just continue processing as usual!!
  */
class DBServiceActor private (dbConfig: DBConfig, supervisorActor: ActorRef)(
    implicit ec: Scheduler)
    extends Actor
    with ActorLogging {

  // TODO: Should we use the PowerPlantService from the AppBindings instead of using a new instance all together?
  val powerPlantService: PowerPlantService[Task] = new PowerPlantService(
    new PowerPlantRepoAsTask(dbConfig)(ec)
  )

  // This will be our subscription to fetch from the database
  val dbSubscription = SingleAssignmentCancelable()

  override def preStart(): Unit = {
    super.preStart()
    log.info("Pre Start DBServiceActor")

    /**
      * We stream events only when this flag is set to true and by default it is
      * in fact set to true! It becomes of use when we run unit tests, so that
      * we can control the sending of events to this actor instead of having
      * this Observable inside this actor sending messages!
      */
    if (dbConfig.enableSubscription) {
      val obs = DBObservable(
        dbConfig.refreshInterval,
        powerPlantService.fetchAllPowerPlants(onlyActive = true).runAsync
      )

      log.info("Activating DB lookup subscription")

      dbSubscription := obs.unsafeSubscribeFn(
        new Subscriber[Seq[PowerPlantRow]] {
          override implicit def scheduler: Scheduler = ec

          override def onNext(elem: Seq[PowerPlantRow]): Future[Ack] = {
            self ! com.inland24.plantsim.models.toPowerPlantsConfig(elem)
            Continue
          }

          override def onError(ex: Throwable): Unit =
            log.error(s"Error ${ex.getMessage}")

          override def onComplete(): Unit = log.info("Complete")
        })
    }
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info("Cancelling DB lookup subscription")
    dbSubscription.cancel()
  }

  override def receive: Receive = {
    case powerPlantsConfig: PowerPlantsConfig =>
      val newEvents = DBServiceActor.toEvents(
        PowerPlantsConfig(DateTime.now(DateTimeZone.UTC),
                          Seq.empty[PowerPlantConfig]),
        powerPlantsConfig
      )
      // Signal these events to SupervisorActor
      if (newEvents.nonEmpty) {
        // send them to the SupervisorActor
        supervisorActor ! SupervisorEvents(newEvents)
      }
      // We can now context become on active, so that subsequent updates are piped
      context.become(
        active(powerPlantsConfig)
      )
  }

  def active(oldPowerPlantsConfig: PowerPlantsConfig): Receive = {
    case newPowerPlantsConfig: PowerPlantsConfig =>
      val newEvents =
        DBServiceActor.toEvents(oldPowerPlantsConfig, newPowerPlantsConfig)
      if (newEvents.nonEmpty) {
        // Signal these events to SupervisorActor
        supervisorActor ! SupervisorEvents(newEvents)
      }
      // We can now context become on active, so that subsequent events could be calculated
      context.become(
        active(newPowerPlantsConfig)
      )
  }
}

object DBServiceActor {

  type PowerPlantConfigMap = Map[Int, PowerPlantConfig]
  type PowerPlantEventsSeq = Seq[PowerPlantDBEvent[PowerPlantConfig]]

  /**
    * Transform a given sequence of old and new state of PowerPlantConfig
    * to a sequence of events. These events will determine how the actors
    * representing the PowerPlant might be stopped, started or re-started
    * depending on whether the PowerPlant is deleted, created or updated.
    */
  def toEvents(oldCfg: PowerPlantsConfig,
               newCfg: PowerPlantsConfig): PowerPlantEventsSeq = {
    val oldMap = oldCfg.powerPlantConfigSeq.map(elem => elem.id -> elem).toMap
    val newMap = newCfg.powerPlantConfigSeq.map(elem => elem.id -> elem).toMap

    def deletedEvents(oldMap: PowerPlantConfigMap,
                      newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      oldMap.keySet
        .filterNot(newMap.keySet)
        .map(id => PowerPlantDeleteEvent(id, oldMap(id))) // No way this is going to throw element not found exception
        .toSeq
    }

    def updatedEvents(oldMap: PowerPlantConfigMap,
                      newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      oldMap.keySet
        .intersect(newMap.keySet)
        .collect {
          case id if !oldMap(id).equals(newMap(id)) =>
            PowerPlantUpdateEvent(id, newMap(id))
        }
        .toSeq
    }

    def createdEvents(oldMap: PowerPlantConfigMap,
                      newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      newMap.keySet
        .filterNot(oldMap.keySet)
        .map(id => PowerPlantCreateEvent(id, newMap(id))) // No way this is going to throw element not found exception
        .toSeq
    }

    deletedEvents(oldMap, newMap) ++ updatedEvents(oldMap, newMap) ++ createdEvents(
      oldMap,
      newMap)
  }

  def props(dbConfig: DBConfig, supervisorActorRef: ActorRef)(
      implicit ec: Scheduler): Props =
    Props(new DBServiceActor(dbConfig, supervisorActorRef)(ec))
}
