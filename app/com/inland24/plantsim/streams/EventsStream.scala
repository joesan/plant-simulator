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

import akka.actor.{Actor, ActorLogging, Props}
import com.inland24.plantsim.models.PowerPlantSignal
import com.inland24.plantsim.models.PowerPlantSignal.{
  DefaultAlert,
  Genesis,
  Transition
}
import com.inland24.plantsim.streams.EventsStream.DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes
import monix.reactive.subjects.ConcurrentSubject

final class EventsStream(
    channel: ConcurrentSubject[PowerPlantSignal, PowerPlantSignal])
    extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    super.preStart()
  }

  override def receive: PartialFunction[Any, Unit] = {
    case t: Transition =>
      channel.onNext(t)

    case g: Genesis =>
      channel.onNext(g)

    case d: DefaultAlert =>
      channel.onNext(d)

    // We want to test the resiliency of the Actor Supervision, so we have this message here!
    case DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes =>
      // Whoosh.... some insane dog wanted a war with me! and he did by sending me this message
      log.info(
        "Sorry mate! I got to go! Division by zero is the next statement that would blow me up. I will be resurrected " +
          "by my supervisor! Make sure please no one sends this message ever")
      (1 / 0) // We know this will blow up

    case x: Any =>
      log.info(s"**** Got Unknown Message $x **** This will just be ignored!")
  }
}
object EventsStream {

  // Be careful when sending this message to the Actor!
  case object DoNotSendThisMessageAsThisIsDangerousButWeHaveItHereForTestingPurposes

  def props(
      publishChannel: ConcurrentSubject[PowerPlantSignal, PowerPlantSignal])
    : Props =
    Props(new EventsStream(publishChannel))
}
