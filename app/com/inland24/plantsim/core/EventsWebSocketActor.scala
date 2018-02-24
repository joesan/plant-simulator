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

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json}
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber

import scala.concurrent.Future


class EventsWebSocketActor(obs: PowerPlantEventObservable, sink: ActorRef, someId: Option[Int])
  extends Actor with ActorLogging {

  private[this] val subscription = SingleAssignmentCancelable()

  override def postStop(): Unit = {
    log.info("Cancelling Event WebSocket Actor **** ")
    subscription.cancel()
    log.info("Cancelling Event WebSocket Subscription")
    super.postStop()
  }

  override def preStart = {
    super.preStart()

    // 1. Our Observable
    val source = someId match {
      case Some(id) => obs.collect { case elem if elem.powerPlantConfig.id == id => Json.toJson(elem) }
      case None => obs.map(elem => Json.toJson(elem))
    }

    // 2. This will be our Subscriber
    val subscriber = new Subscriber[JsValue] {
      override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

      override def onError(ex: Throwable): Unit = {
        log.warning(s"Error while serving a web-socket stream", ex)
        sink ! Json.obj(
          "event" -> "error",
          "type" -> ex.getClass.getName,
          "message" -> ex.getMessage,
          "timestamp" -> DateTime.now()
        )

        self ! PoisonPill
      }

      override def onComplete(): Unit = {
        sink ! Json.obj("event" -> "complete", "timestamp" -> DateTime.now())
        self ! PoisonPill
      }

      override def onNext(elem: JsValue): Future[Ack] = {
        self ! Json.prettyPrint(elem)
        Continue
      }
    }
/*
    val subscriberSS = new Subscriber[JsValue] {
      def onSubscribe(s: Subscription): Unit = {
        println(s"WebSocket Opened ************** ")
        subscription := s
      }

      def onNext(json: JsValue): Unit = {
        log.info(s"Got a new message **** $json")
        sink ! json
      }

      def onError(t: Throwable): Unit = {
        log.warning(s"Error while serving a web-socket stream", t)
        sink ! Json.obj(
          "event" -> "error",
          "type" -> t.getClass.getName,
          "message" -> t.getMessage,
          "timestamp" -> DateTime.now(DateTimeZone.UTC))

        context.stop(self)
      }

      def onComplete(): Unit = {
        sink ! Json.obj("event" -> "complete", "timestamp" -> DateTime.now(DateTimeZone.UTC))
        context.stop(self)
      }
    } */

    // 3. The Subscription that is going to push our events outside
    subscription := source.subscribe(subscriber)
  }

  def receive = {
    case e: String =>
      sink ! e
  }
}
object EventsWebSocketActor {

  def props(source: PowerPlantEventObservable, out: ActorRef, someId: Option[Int]) =
    Props(new EventsWebSocketActor(source, out, someId))
}