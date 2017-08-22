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

import com.inland24.plantsim.services.database.models.PowerPlantRow
import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.FutureUtils.extensions._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

import play.api.Logger


final class DBObservable private (period: FiniteDuration, f: => Future[Seq[PowerPlantRow]])
  extends Observable[Seq[PowerPlantRow]] {

  def unsafeSubscribeFn(subscriber: Subscriber[Seq[PowerPlantRow]]): Cancelable = {
    implicit val s = subscriber.scheduler

    def request() = {
      Logger.info("Looking up the database for new updates")
      val safe = f.materialize.map { // materialize is cool!
        case Success(r) =>
          Some(r)
        case Failure(ex) =>
          Logger.error("Error while querying the database", ex)
          None
      }

      Observable.fromFuture(safe)
    }

    Observable.intervalWithFixedDelay(period)
      .flatMap(_ => request())
      .collect { case Some(r) => r }
      .distinctUntilChanged
      .unsafeSubscribeFn(subscriber)
  }
}

object
DBObservable {
  /**
    * Builder for [[DBObservable]].
    */
  def apply(period: FiniteDuration, f: => Future[Seq[PowerPlantRow]]): DBObservable = {
    new DBObservable(period, f)
  }
}