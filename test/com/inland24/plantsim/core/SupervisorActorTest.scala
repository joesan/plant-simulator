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

import monix.execution.FutureUtils.extensions._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig, UnknownConfig}
import com.inland24.plantsim.models.PowerPlantDBEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType, UnknownType}
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantType}
import com.inland24.plantsim.services.database.DBServiceActor.PowerPlantEventsSeq
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


class SupervisorActorTest extends TestKit(ActorSystem("SupervisorActorTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  // Use a default AppConfig
  val appCfg = AppConfig.load()
  implicit val ec = monix.execution.Scheduler.Implicits.global

  // Let us create our SupervisorActor instance
  val supervisorActor = system.actorOf(
    SupervisorActor.props(appCfg, PowerPlantEventObservable(ec))
  )

  def powerPlantCfg(powerPlantId: Int, powerPlantType: PowerPlantType): PowerPlantConfig = powerPlantType match {
    case RampUpType =>
      RampUpTypeConfig(
        powerPlantType = RampUpType,
        id = powerPlantId,
        name = s"$powerPlantId",
        minPower = powerPlantId * 10.0,
        maxPower = powerPlantId * 20.0,
        rampPowerRate = powerPlantId * 5.0,
        rampRateInSeconds = 2.seconds
      )
    case OnOffType =>
      OnOffTypeConfig(
        powerPlantType = OnOffType,
        id = powerPlantId,
        name = s"$powerPlantId",
        minPower = powerPlantId * 10.0,
        maxPower = powerPlantId * 20.0
      )
    case UnknownType  =>
      UnknownConfig(
        powerPlantType = UnknownType,
        id = powerPlantId,
        name = s"$powerPlantId",
        minPower = powerPlantId * 10.0,
        maxPower = powerPlantId * 20.0
      )
  }

  def powerPlantCreateEvent(powerPlantId: Int, powerPlantCfg: PowerPlantConfig) = {
    PowerPlantCreateEvent[PowerPlantConfig](powerPlantId, powerPlantCfg)
  }

  def childActorRef(powerPlantId: Int) = {
    system.actorSelection(
      s"akka://SupervisorActorTest/user/*/${appCfg.appName}-$powerPlantId"
    ).resolveOne(2.seconds).materialize
  }

  "SupervisorActor" must {

    // Let us create 3 events, one PowerPlantCreateEvent, a PowerPlantUpdateEvent and  a PowerPlantDeleteEvent
    val onOffTypePowerPlantEventsSeq = Seq(
      PowerPlantCreateEvent[OnOffTypeConfig](1, powerPlantCfg(1, OnOffType).asInstanceOf[OnOffTypeConfig]),
      PowerPlantUpdateEvent[OnOffTypeConfig](2, powerPlantCfg(2, OnOffType).asInstanceOf[OnOffTypeConfig]),
      PowerPlantDeleteEvent[OnOffTypeConfig](3, powerPlantCfg(3, OnOffType).asInstanceOf[OnOffTypeConfig])
    )

    // Let us create 3 events, one PowerPlantCreateEvent, a PowerPlantUpdateEvent and  a PowerPlantDeleteEvent
    val rampUpTypePowerPlantEventsSeq = Seq(
      PowerPlantCreateEvent[RampUpTypeConfig](4, powerPlantCfg(4, RampUpType).asInstanceOf[RampUpTypeConfig]),
      PowerPlantUpdateEvent[RampUpTypeConfig](5, powerPlantCfg(5, RampUpType).asInstanceOf[RampUpTypeConfig]),
      PowerPlantDeleteEvent[RampUpTypeConfig](6, powerPlantCfg(6, RampUpType).asInstanceOf[RampUpTypeConfig])
    )

    "Create a new PowerPlant Actor when a PowerPlantCreate event is received" in {
      val createEventsSeq = Seq(
        onOffTypePowerPlantEventsSeq.head,
        rampUpTypePowerPlantEventsSeq.head
      ).asInstanceOf[PowerPlantEventsSeq]

      within(3.seconds) {
        supervisorActor ! SupervisorEvents(createEventsSeq)
        expectNoMsg()
      }

      // Now check if the corresponding ActorRef's are created!
      Await.result(childActorRef(createEventsSeq.head.id.toInt), 3.seconds) match {
        case Success(_) => // Nothing to do!
        case Failure(_) =>
          fail(s"expected child Actor to be found for " +
            s"OnOffType PowerPlant with id ${createEventsSeq.head.id}, but was not found"
          )
      }

      Await.result(childActorRef(createEventsSeq.last.id.toInt), 3.seconds) match {
        case Success(_) => // Nothing to do!
        case Failure(_) =>
          fail(s"expected child Actor to be found for " +
            s"RampUpType PowerPlant with id ${createEventsSeq.last.id}, but was not found"
          )
      }
    }

    "Stop and Re-start a running PowerPlant Actor when a PowerPlantUpdate event is received" in {
      // First let us create the Actor instances
      val createEventsSeq = Seq(
        PowerPlantCreateEvent[OnOffTypeConfig](2, powerPlantCfg(2, OnOffType).asInstanceOf[OnOffTypeConfig]),
        PowerPlantCreateEvent[RampUpTypeConfig](5, powerPlantCfg(5, RampUpType).asInstanceOf[RampUpTypeConfig])
      ).asInstanceOf[PowerPlantEventsSeq]

      within(5.seconds) {
        supervisorActor ! SupervisorEvents(createEventsSeq)
        expectNoMsg()
      }

      // Now let us send Update events, so that the Actors are re-started!
      val updateEventsSeq = Seq(
        onOffTypePowerPlantEventsSeq.tail.head,
        rampUpTypePowerPlantEventsSeq.tail.head
      ).asInstanceOf[PowerPlantEventsSeq]

      within(5.seconds) {
        supervisorActor ! SupervisorEvents(updateEventsSeq)
        expectNoMsg()
      }

      // Now check if the corresponding ActorRef's are created!
      Await.result(childActorRef(updateEventsSeq.head.id.toInt), 5.seconds) match {
        case Success(_) => // Nothing to do!
        case Failure(_) =>
          fail(s"expected child Actor to be found for " +
            s"OnOffType PowerPlant with id ${updateEventsSeq.head.id}, but was not found"
          )
      }

      Await.result(childActorRef(updateEventsSeq.last.id.toInt), 5.seconds) match {
        case Success(_) => // Nothing to do!
        case Failure(_) =>
          fail(s"expected child Actor to be found for " +
            s"RampUpType PowerPlant with id ${updateEventsSeq.last.id}, but was not found"
          )
      }
    }

    "Stop a running PowerPlant Actor when a PowerPlantDelete event is received" in {
      // First let us start the actors, so that we can stop them later
      val createEventsSeq = Seq(
        PowerPlantCreateEvent[OnOffTypeConfig](200, powerPlantCfg(200, OnOffType).asInstanceOf[OnOffTypeConfig]),
        PowerPlantCreateEvent[RampUpTypeConfig](201, powerPlantCfg(201, RampUpType).asInstanceOf[RampUpTypeConfig])
      ).asInstanceOf[PowerPlantEventsSeq]

      within(5.seconds) {
        supervisorActor ! SupervisorEvents(createEventsSeq)
        expectNoMsg()
      }

      val deleteEventsSeq = Seq(
        PowerPlantDeleteEvent[OnOffTypeConfig](200, powerPlantCfg(200, OnOffType).asInstanceOf[OnOffTypeConfig]),
        PowerPlantDeleteEvent[RampUpTypeConfig](201, powerPlantCfg(201, RampUpType).asInstanceOf[RampUpTypeConfig])
      ).asInstanceOf[PowerPlantEventsSeq]

      within(6.seconds) {
        supervisorActor ! SupervisorEvents(deleteEventsSeq)
        expectNoMsg()
      }

      // Now check if the corresponding ActorRef's are created!
      Await.result(childActorRef(deleteEventsSeq.head.id.toInt), 3.seconds) match {
        case Success(_) =>
          fail(s"expected child Actor Not to be found for " +
            s"OnOffType PowerPlant with id ${deleteEventsSeq.head.id}, but was not found"
          )
        case Failure(ex) => // Nothing to do, as we expect a Failure
      }

      Await.result(childActorRef(deleteEventsSeq.last.id.toInt), 3.seconds) match {
        case Success(_) =>
          fail(s"expected child Actor Not to be found for " +
            s"RampUpType PowerPlant with id ${deleteEventsSeq.last.id}, but was found"
          )
        case Failure(_) => // Nothing to do, as we expect a Failure
      }
    }

    "Do nothing when an event of type UnknownConfig is encountered" in {
      val createEventsSeq = Seq(
        PowerPlantCreateEvent[UnknownConfig](100, powerPlantCfg(100, UnknownType).asInstanceOf[UnknownConfig])
      ).asInstanceOf[PowerPlantEventsSeq]

      within(3.seconds) {
        supervisorActor ! SupervisorEvents(createEventsSeq)
        expectNoMsg()
      }

      // Now check if the corresponding ActorRef's are not created!
      Await.result(childActorRef(createEventsSeq.head.id.toInt), 3.seconds) match {
        case Success(_) =>
          fail(s"expected child Actor Not to be found for " +
            s"UnknownType PowerPlant with id ${createEventsSeq.head.id}, but was found"
          )
        case Failure(_) => // Nothing to do, as we expect a Failure
      }
    }

    "Start an Actor if a PowerPlantUpdate event is received, but there was no Actor already running" in {
      val updateEventsSeq = Seq(
        PowerPlantUpdateEvent[OnOffTypeConfig](101, powerPlantCfg(101, OnOffType).asInstanceOf[OnOffTypeConfig])
      ).asInstanceOf[PowerPlantEventsSeq]

      within(3.seconds) {
        supervisorActor ! SupervisorEvents(updateEventsSeq)
        expectNoMsg()
      }

      // Now check if the corresponding ActorRef's are created!
      Await.result(childActorRef(updateEventsSeq.head.id.toInt), 3.seconds) match {
        case Success(_) => // Nothing to do!
        case Failure(_) =>
          fail(s"expected child Actor Not to be found for " +
            s"UnknownType PowerPlant with id ${updateEventsSeq.head.id}, but was found"
          )
      }
    }
  }
}