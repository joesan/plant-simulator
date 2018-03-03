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

import com.inland24.plantsim.models.PowerPlantSignal
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject

final class PowerPlantEventObservable private (
    underlying: ConcurrentSubject[PowerPlantSignal, PowerPlantSignal])
    extends ConcurrentSubject[PowerPlantSignal, PowerPlantSignal] {

  override def unsafeSubscribeFn(s: Subscriber[PowerPlantSignal]) = {
    underlying.unsafeSubscribeFn(s)
  }

  override def size: Int = underlying.size

  override def onNext(elem: PowerPlantSignal): Ack = {
    underlying.onNext(elem)
  }

  override def onError(ex: Throwable): Unit = underlying.onError(ex)

  override def onComplete(): Unit = {
    underlying.onComplete()
  }
}
object PowerPlantEventObservable {

  def apply(s: Scheduler): PowerPlantEventObservable = {
    implicit val s = monix.execution.Scheduler.Implicits.global
    new PowerPlantEventObservable(
      ConcurrentSubject.publish[PowerPlantSignal](Unbounded))
  }
}
