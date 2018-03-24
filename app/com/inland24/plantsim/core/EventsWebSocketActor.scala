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
import akka.util.Timeout
import com.inland24.plantsim.models.PowerPlantActorMessage.TelemetrySignalsMessage
import org.joda.time.{DateTime, DateTimeZone}
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import play.api.libs.json._
import play.api.libs.json.JodaWrites._

import scala.concurrent.Future

class EventsWebSocketActor(source: Observable[JsValue], sink: ActorRef)
    extends Actor
    with ActorLogging {

  private[this] val subscription = SingleAssignmentCancelable()

  override def postStop(): Unit = {
    subscription.cancel()
    log.info("Cancelled EventsWebSocketActor Subscription ****")
    super.postStop()
  }

  override def preStart(): Unit = {
    super.preStart()

    log.info("**** Started EventsWebSocketActor Subscription")

    // 1. This will be our Subscriber to the source Observable
    val subscriber = new Subscriber[JsValue] {
      override implicit def scheduler: Scheduler =
        monix.execution.Scheduler.Implicits.global

      override def onError(ex: Throwable): Unit = {
        log.warning(s"Error while serving a web-socket stream", ex)
        sink ! Json.obj(
          "event" -> "error",
          "type" -> ex.getClass.getName,
          "message" -> ex.getMessage,
          "timestamp" -> DateTime.now(DateTimeZone.UTC)
        )
        self ! PoisonPill
      }

      override def onComplete(): Unit = {
        sink ! Json.obj(
          "event" -> "complete",
          "timestamp" -> DateTime.now(DateTimeZone.UTC)
        )
        self ! PoisonPill
      }

      override def onNext(elem: JsValue): Future[Ack] = {
        self ! elem
        Continue
      }
    }

    // 3. The Subscription that is going to push our events outside
    subscription := source.subscribe(subscriber)
  }

  def receive: PartialFunction[Any, Unit] = {
    case jsValue: JsValue =>
      sink ! jsValue.toString
    case e: String =>
      log.info(s"Client sent a Message $e")
  }
}
object EventsWebSocketActor {

  implicit val writes: Writes[Map[String, Any]] = (o: Map[String, Any]) => {
    JsObject(
      o.map {
        case (key, value) =>
          key -> (value match {
            case x: Boolean => JsBoolean(x)
            case x: String  => JsString(x)
            case x: Double  => JsNumber(x)
            case _          => JsNull
          })
      }
    )
  }

  // Utility method to convert to proper types in the resulting JSON
  def telemetrySignals(signalsAsMap: Map[String, String]): Map[String, Any] = {
    signalsAsMap.map {
      case (key, value) if key == "activePower" || key == "setPoint" =>
        key -> value.toDouble
      case (key, value)
          if key == "isOnOff" || key == "isDispatched" || key == "isAvailable" =>
        key -> value.toBoolean
      case (key, value) =>
        key -> value
    }
  }

  def eventsAndAlerts(
      someId: Option[Int],
      source: PowerPlantEventObservable): Observable[JsValue] = {
    someId match {
      case Some(id) =>
        source.collect {
          case elem if elem.powerPlantConfig.id == id => Json.toJson(elem)
        }
      case None => source.map(elem => Json.toJson(elem))
    }
  }

  def telemetrySignals(id: Int,
                       powerPlantActorRef: ActorRef): Observable[JsValue] = {
    import scala.concurrent.duration._
    import akka.pattern.ask
    implicit val timeOut: Timeout = 3.seconds
    // Every 4 seconds, we ask the Actor for the signals
    Observable
      .interval(4.seconds)
      .flatMap(
        _ =>
          Observable.fromFuture(
            (powerPlantActorRef ? TelemetrySignalsMessage)
              .mapTo[Map[String, String]]
        ))
      .map(signalsMap => Json.toJson(telemetrySignals(signalsMap)))
  }

  def props(source: Observable[JsValue], sink: ActorRef) =
    Props(new EventsWebSocketActor(source, sink))
}
