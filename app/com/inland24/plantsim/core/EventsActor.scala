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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsNumber, JsValue, Json}
import monix.execution.rstreams.SingleAssignmentSubscription
import org.reactivestreams.{Subscriber, Subscription}
import monix.execution.Scheduler.Implicits.global

import scala.util.{Failure, Success, Try}


class EventsActor(obs: PowerPlantEventObservable, sink: ActorRef, someId: Option[Int])
  extends Actor with ActorLogging {

  private[this] val subscription = SingleAssignmentSubscription()

  override def postStop(): Unit = {
    subscription.cancel()
    super.postStop()
  }

  override def preStart = {
    super.preStart()

    val subscriberSS = new Subscriber[JsValue] {
      def onSubscribe(s: Subscription): Unit = {
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
    }

    val source = obs.map(elem => {
      val json = Json.toJson(elem)
      println(json)
      println("JSON is ***********")
      json
    })
    source.toReactivePublisher.subscribe(subscriberSS)
  }

  def receive = {
    case msg: String =>
      sink ! s"message received is $msg"
    case JsNumber(nr) if nr > 0 =>
      println(s"nr is ********* $nr")
      Try(nr.toLongExact).foreach(subscription.request)
  }
}
object EventsActor {

  /**
    * For pattern matching request events.
    */
  object Request {
    def unapply(value: Any): Option[Long] =
      value match {
        case str: String =>
          str.trim match {
            case IsInteger(integer) =>
              try Some(integer.toLong).filter(_ > 0) catch {
                case _: NumberFormatException =>
                  None
              }
            case _ =>
              None
          }
        case number: Int =>
          Some(number.toLong)
        case number: Long =>
          Some(number)
        case _ =>
          None
      }

    val IsInteger = """^([-+]?\d+)$""".r
  }

  def props(source: PowerPlantEventObservable, out: ActorRef, someId: Option[Int]) =
    Props(new EventsActor(source, out, someId))
}