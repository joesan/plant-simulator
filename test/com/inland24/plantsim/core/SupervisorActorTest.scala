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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig}
import com.inland24.plantsim.models.PowerPlantEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantEvent, PowerPlantType}
import com.inland24.plantsim.services.database.DBServiceActor.PowerPlantEventsSeq
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


class SupervisorActorTest extends TestKit(ActorSystem("SupervisorActorTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  // Use a default AppConfig
  val appCfg = AppConfig.load()

  // Let us create our SupervisorActor instance
  val supervisorActor = system.actorOf(
    SupervisorActor.props(appCfg)(monix.execution.Scheduler.Implicits.global)
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
    case _ =>
      OnOffTypeConfig(
        powerPlantType = OnOffType,
        id = powerPlantId,
        name = s"$powerPlantId",
        minPower = powerPlantId * 10.0,
        maxPower = powerPlantId * 20.0
      )
  }

  def powerPlantCreateEvent(powerPlantId: Int, powerPlantCfg: PowerPlantConfig) = {
    PowerPlantCreateEvent[PowerPlantConfig](powerPlantId, powerPlantCfg)
  }

  "SupervisorActor" must {

    // Let us create 3 events, one PowerPlantCreateEvent, a PowerPlantUpdateEvent and  a PowerPlantDeleteEvent
    val powerPlantEventsSeq = Seq(
      PowerPlantCreateEvent[OnOffTypeConfig](1, powerPlantCfg(1, OnOffType).asInstanceOf[OnOffTypeConfig]),
      PowerPlantUpdateEvent[OnOffTypeConfig](2, powerPlantCfg(2, OnOffType).asInstanceOf[OnOffTypeConfig]),
      PowerPlantDeleteEvent[OnOffTypeConfig](3, powerPlantCfg(3, OnOffType).asInstanceOf[OnOffTypeConfig])
    )

    "Create a new PowerPlant Actor when a PowerPlantCreate event is received" in {
      supervisorActor ! SupervisorEvents(Seq(powerPlantEventsSeq.head).asInstanceOf[PowerPlantEventsSeq])
    }

    "Stop and Re-start a running PowerPlant Actor when a PowerPlantUpdate event is received" in {

    }

    "Stop a running PowerPlant Actor when a PowerPlantDelete event is received" in {

    }
  }
}