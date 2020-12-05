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

package com.inland24.plantsim.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.core.{
  AppBindings,
  PowerPlantEventObservable,
  SupervisorActor
}
import com.inland24.plantsim.models.PowerPlantActorMessage.{
  OutOfServiceMessage,
  ReturnToServiceMessage
}
import com.inland24.plantsim.models.PowerPlantConfig.{
  OnOffTypeConfig,
  RampUpTypeConfig
}
import com.inland24.plantsim.models.PowerPlantDBEvent.PowerPlantCreateEvent
import com.inland24.plantsim.models.PowerPlantSignal
import com.inland24.plantsim.models.PowerPlantSignal.Transition
import com.inland24.plantsim.models.PowerPlantState.OutOfService
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.DBServiceActor.PowerPlantEventsSeq
import com.inland24.plantsim.streams.EventsStream.DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import org.scalatest.{BeforeAndAfterAll, Ignore}
import monix.execution.FutureUtils.extensions._
import monix.execution.cancelables.SingleAssignCancelable
import monix.reactive.observers.Subscriber
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.matchers.should

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@Ignore
class EventsStreamTest
    extends TestKit(ActorSystem("EventsStreamTest"))
    with ImplicitSender
    with AnyFeatureSpecLike
    with should.Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  // Use a default AppConfig
  val appBindings: AppBindings =
    //AppBindings(system, ActorMaterializer()(system))
    AppBindings(system)
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global

  // Utility to resolve an ActorRef
  def childActorRef(powerPlantId: Int): Future[Try[ActorRef]] = {
    system
      .actorSelection(
        s"akka://EventsStreamTest/user/*/${appBindings.appConfig.appName}-$powerPlantId"
      )
      .resolveOne(2.seconds)
      .materialize
  }

  // Let us create our SupervisorActor instance
  val supervisorActor: ActorRef = system.actorOf(
    SupervisorActor.props(appBindings.appConfig, PowerPlantEventObservable(ec))
  )

  // We create one RampUpType and one OnOffType PowerPlant
  val ramUpTypeCfg: RampUpTypeConfig = RampUpTypeConfig(
    powerPlantType = RampUpType,
    id = 1,
    name = "rampUpType-1",
    minPower = 100.0,
    maxPower = 800.0,
    rampPowerRate = 100.0,
    rampRateInSeconds = 2.seconds
  )
  val onOffTypeCfg: OnOffTypeConfig = OnOffTypeConfig(
    powerPlantType = OnOffType,
    id = 2,
    name = "onOffType-2",
    minPower = 400.0,
    maxPower = 900.0
  )

  // We send these create events to the SupervisorActor
  val createEventsSeq: PowerPlantEventsSeq = Seq(
    PowerPlantCreateEvent[RampUpTypeConfig](1, ramUpTypeCfg),
    PowerPlantCreateEvent[OnOffTypeConfig](2, onOffTypeCfg)
  ).asInstanceOf[PowerPlantEventsSeq]

  Feature("EventsStream") {
    Scenario("push events and alerts from PowerPlant into it's out channel") {

      val subscription = SingleAssignCancelable()
      // 0. We need a subscriber for the globalChannel
      val subscriber = new Subscriber[PowerPlantSignal] {
        override implicit def scheduler: Scheduler = ec

        override def onNext(elem: PowerPlantSignal): Future[Ack] = elem match {
          case t: Transition
              if t.powerPlantConfig.id == 2 && t.newState == OutOfService =>
            // We got the message we are interested in (TODO: What to do....??)
            Continue

          case t =>
            println(s"Got a Message $t")
            Continue // Nothing to do, so we Continue
        }

        override def onError(ex: Throwable): Unit = ???

        override def onComplete(): Unit = ???
      }
      subscription := appBindings.globalChannel.subscribe(subscriber)

      // 1. First we signal the SupervisorActor to create the PowerPlant child actors
      within(5.seconds) {
        supervisorActor ! SupervisorEvents(createEventsSeq)
        expectNoMessage
      }

      // 2. We find the Actor instance for the OnOffType and ask it to go to OutOfService
      Await.result(childActorRef(2), 3.seconds) match {
        case Success(actorRef) =>
          // This is an event and this should resurrect out EventsStream actor
          actorRef ! DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes
          Thread.sleep(5000)

          // The Supervisor should have resurrected our EventsStream Actor
          actorRef ! OutOfServiceMessage
          Thread.sleep(5000)

          actorRef ! ReturnToServiceMessage
          Thread.sleep(5000)

          actorRef ! OutOfServiceMessage
          Thread.sleep(5000)

        case Failure(ex) =>
          fail(
            s"Something went wrong when searching for an ActorRef " +
              s"that should have existed failure message is ${ex.getMessage}")
      }

      // Clean up
      subscription.cancel()
    }
  }
}
