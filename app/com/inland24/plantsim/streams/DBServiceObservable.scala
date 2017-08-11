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

import com.typesafe.scalalogging.LazyLogging
import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.execution.FutureUtils.extensions._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * We are streaming data from the database table and passing it to
  * whoever that subscribes to this stream. The kind of data that we
  * stream depends on the caller and is governed by the function that
  * he passes to the constructor.
  * TODO: This seems not to work!
  */
class DBServiceObservable[T, U] private (refreshInterval: FiniteDuration, fn: => Future[T])
  (mapper: T => U)(implicit ec: ExecutionContext)
  extends Observable[U] with LazyLogging {

  override def unsafeSubscribeFn(subscriber: Subscriber[U]): Cancelable = {

    def underlying() = {
      logger.info("Checking the database for PowerPlant updates")
      println("Checking the database for PowerPlant updates")
      val someFuture = fn.materialize.map {
        case Success(succ) => Some(succ)
        case Failure(ex) =>
          logger.warn(s"Unexpected error when streaming from the database :: Failure Message :: ${ex.getMessage}")
          None
      }

      Observable.fromFuture(someFuture)
    }

    Observable
      .intervalWithFixedDelay(refreshInterval)
      .flatMap(_ => underlying())
      //.distinctUntilChanged
      // We map it to the target type we need!
      .collect { case Some(powerPlantsSeq) => mapper(powerPlantsSeq) }
      .map(elem => {
        println(elem)
        elem
      })
      .unsafeSubscribeFn(subscriber)
  }
}
object DBServiceObservable {

  def powerPlantDBServiceObservable[T, U]
  (refreshInterval: FiniteDuration, fn: Future[T])
    (mapper: T => U)(implicit ec: ExecutionContext): DBServiceObservable[T, U] =
    new DBServiceObservable[T, U](refreshInterval, fn)(mapper)(ec)
}