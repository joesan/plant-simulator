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

import akka.actor.ActorSystem
import com.inland24.plantsim.models.PowerPlantConfig
import com.inland24.plantsim.models.PowerPlantConfig.PowerPlantsConfig
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.{DBService, DBServiceSpec}
import com.typesafe.scalalogging.LazyLogging
import monix.execution.{Ack, Scheduler}
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.observers.Subscriber
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.concurrent.duration._
import scala.util.Success


class DBObservableTest extends DBServiceSpec with WordSpecLike with Matchers
  with BeforeAndAfterAll with LazyLogging {

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    super.h2SchemaDrop()
    actorSystem.terminate()
  }

  // We use this for testing purposes
  val actorSystem = ActorSystem("test-scheduler")

  // Utility method to delay execution of a Future
  def delayedFuture[T](delay: FiniteDuration)(block: => T)(implicit executor : ExecutionContext): Future[T] = {
    val promise = Promise[T]

    actorSystem.scheduler.scheduleOnce(delay) {
      try {
        val result = block
        promise.complete(Success(result))
      } catch {
        case t: Throwable => promise.failure(t)
      }
    }
    promise.future
  }

  // This will be our ThreadPool
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.Implicits.global

  // This will be our service instance
  val dbService = DBService.asTask(config.dbConfig)(ec)

  // We want to fetch updates from the database every 2 seconds
  val interval: FiniteDuration = 2.seconds

  // Our subscription that we cancel after tests are done
  val dbSubscription = SingleAssignmentCancelable()

  "DBObservable" must {

    "fetch PowerPlant updates at regular intervals given" in {
      val dbObservable = DBObservable(interval, dbService.allPowerPlants(fetchOnlyActive = true).runAsync)

      def newPowerPlantRow(powerPlantId: Int) = {
        PowerPlantRow(
          id = powerPlantId,
          orgName = s"joesan$powerPlantId",
          isActive = true,
          minPower = 100.0,
          maxPower = 400.0,
          powerPlantTyp = OnOffType,
          createdAt = getNowAsDateTime(),
          updatedAt = getNowAsDateTime()
        )
      }

      // Upon every update from the database, we mutate this variable so that we could check our assertions
      val powerPlantsConfig = PowerPlantsConfig(getNowAsDateTime(), Seq.empty[PowerPlantConfig])

      dbSubscription := dbObservable.unsafeSubscribeFn (new Subscriber[Seq[PowerPlantRow]] {
        override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

        /*
         * Upon the first onNext event, we would just get all active PowerPlant's
         * from the database. Once we get that, we add a new entry to the PowerPlant
         * table, so upon next call to the onNext, we should have this entry picked up
         * by our DBObservable. If we can assert for this entry, our test is successful!
         */
        override def onNext(elem: Seq[PowerPlantRow]): Future[Ack] = {
          // Let us now make a new PowerPlant entry in the database
          elem.find(row => row.id == 2000) match {
            case Some(_) =>
              powerPlantsConfig.copy(
                powerPlantConfigSeq = com.inland24.plantsim.models.toPowerPlantsConfig(elem).powerPlantConfigSeq
              )
            case None =>
              // We do not yet have it, so let's add a new entry
              dbService.newPowerPlant(newPowerPlantRow(2000))
          }
          Continue
        }

        override def onError(ex: Throwable): Unit = fail("error when streaming updates from the database")

        override def onComplete(): Unit = logger.info("complete")
      })

      def block() = {
        powerPlantsConfig.powerPlantConfigSeq.find(_.id == 2000) match {
          case Some(_) =>
            logger.info("test successful")
          case None =>
            fail("Expected PowerPlantRow with id = 2000 but was not found!")
        }

        // We are done with the test, so we signal onComplete
        dbSubscription.cancel()
      }

      // we wait for 6 seconds before we check out assertion
      delayedFuture(interval * 3)(block())(ec)
    }
  }
}