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

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.pattern.pipe
import com.inland24.plantsim.config.DBConfig
import com.inland24.plantsim.models.PowerPlantConfig.PowerPlantsConfig
import com.inland24.plantsim.models.PowerPlantEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantEvent}
import com.inland24.plantsim.services.database.DBServiceActor.{GetActivePowerPlants, PowerPlantEvents, PowerPlantEventsSeq}
import com.inland24.plantsim.streams.DBServiceObservable
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable

import scala.concurrent.duration._

// TODO: pass in the execution context
// TODO: start emitting events for updates
/**
  * This Actor is responsible for reacting to updates on a PowerPlant
  * in the database. So whenever a PowerPlant is updated, the update
  * is pushed into this actor via the underlying DBServiceObservable
  * and this update is then interpreted accordingly if it is a create
  * update or a delete of a PowerPlant. The subsequent events are then
  * emitted when asked for the events.
  *
  * TODO: If the database is down the stream should not throw an error!!!
  * but rather it should just continue processing as usual!!
  */
class DBServiceActor(dbConfig: DBConfig) extends Actor with ActorLogging {

  // TODO: revisit this timeout duration, should come from parameters
  implicit val timeout: akka.util.Timeout = 5.seconds

  // TODO: import scheduler from method parameters
  implicit val scheduler = monix.execution.Scheduler.Implicits.global

  // This represents the PowerPlantDBService instance
  val powerPlantDBService = DBService(dbConfig)(scheduler)

  // This will be our subscription to fetch from the database
  val powerPlantDBSubscription = SingleAssignmentCancelable()

  // This will be our Observable that will stream events from the database
  val obs =
    DBServiceObservable.powerPlantDBServiceObservable(
      dbConfig.refreshInterval,
      powerPlantDBService.allPowerPlants(fetchOnlyActive = true)
    )(com.inland24.plantsim.models.toPowerPlantsConfig)

  override def preStart(): Unit = {
    super.preStart()
    log.info("Pre-start DBServiceActor")

    // On the start, we signal the set of active power plants
    powerPlantDBService.allPowerPlants(fetchOnlyActive = true) pipeTo self
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info("Cancelling DB lookup subscription")
    powerPlantDBSubscription.cancel()
  }

  override def receive: Receive = {
    case powerPlantsConfig: PowerPlantsConfig =>

      // Subscribe to the Observable for fetching updates to the PowerPlant and pipe it to self
      powerPlantDBSubscription := obs.subscribe { update =>
        (self ? update).map(_ => Continue)
      }

      // We can now context become on active, so that subsequent updates are piped
      context.become(
        active(
          powerPlantsConfig,
          Seq.empty[PowerPlantEvent[PowerPlantConfig]]
        )
      )
  }

  def active(activePowerPlantsConfig: PowerPlantsConfig, events: PowerPlantEventsSeq): Receive = {
    case newPowerPlantsConfig: PowerPlantsConfig =>
      context.become(
        active(
          newPowerPlantsConfig,
          DBServiceActor.toEvents(activePowerPlantsConfig, newPowerPlantsConfig)
        )
      )
    case GetActivePowerPlants =>
      sender() ! activePowerPlantsConfig
    case PowerPlantEvents =>
      sender() ! events
  }
}

object DBServiceActor {

  type PowerPlantConfigMap = Map[Long, PowerPlantConfig]
  type PowerPlantEventsSeq = Seq[PowerPlantEvent[PowerPlantConfig]]

  sealed trait Message
  case object GetActivePowerPlants extends Message
  case object PowerPlantEvents extends Message

  /**
    * Transform a given sequence of old and new state of PowerPlantConfig
    * to a sequence of events. These events will determine how the actors
    * representing the PowerPlant might be stopped, started or re-started
    * depending on whether the PowerPlant is deleted, created or updated.
    */
  def toEvents(oldCfg: PowerPlantsConfig, newCfg: PowerPlantsConfig): PowerPlantEventsSeq = {
    val oldMap = oldCfg.powerPlantConfigSeq.map(elem => elem.id -> elem).toMap
    val newMap = newCfg.powerPlantConfigSeq.map(elem => elem.id -> elem).toMap

    def deletedEvents(oldMap: PowerPlantConfigMap, newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      oldMap.keySet.filterNot(newMap.keySet)
        .map(id => PowerPlantDeleteEvent(id, oldMap(id))) // No way this is going to throw element not found exception
        .toSeq
    }

    def updatedEvents(oldMap: PowerPlantConfigMap, newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      oldMap.keySet.intersect(newMap.keySet)
        .collect {
          case id if !oldMap(id).equals(newMap(id)) => PowerPlantUpdateEvent(id, newMap(id))
        }
        .toSeq
    }

    def createdEvents(oldMap: PowerPlantConfigMap, newMap: PowerPlantConfigMap): PowerPlantEventsSeq = {
      newMap.keySet.filterNot(oldMap.keySet)
        .map(id => PowerPlantCreateEvent(id, newMap(id))) // No way this is going to throw element not found exception
        .toSeq
    }

    deletedEvents(oldMap, newMap) ++ updatedEvents(oldMap, newMap) ++ createdEvents(oldMap, newMap)
  }

  def props(dbConfig: DBConfig): Props =
    Props(new DBServiceActor(dbConfig))
}