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

package com.inland24.plantsim.services.database

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.SupervisorActor.SupervisorEvents
import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, PowerPlantsConfig, RampUpTypeConfig}
import com.inland24.plantsim.models.PowerPlantDBEvent.{PowerPlantCreateEvent, PowerPlantDeleteEvent, PowerPlantUpdateEvent}
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.models.{PowerPlantConfig, PowerPlantDBEvent, PowerPlantType}
import com.inland24.plantsim.services.database.repository.impl.PowerPlantRepoAsTask
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

// ***** NOTE: This import should be here, otherwise it won't compile
import monix.cats._
// *****

class DBServiceActorTest extends TestKit(ActorSystem("DBServiceActorTest"))
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with DBServiceSpec with LazyLogging {

  implicit val ec = monix.execution.Scheduler.Implicits.global
  implicit val timeout: akka.util.Timeout = 3.seconds

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    super.h2SchemaDrop()
    TestKit.shutdownActorSystem(system)
  }

  val testOnOffConfig = OnOffTypeConfig(
    id = 1,
    name = "1",
    minPower = 10.0,
    maxPower = 20.0,
    powerPlantType = PowerPlantType.OnOffType
  )

  val testRampUpConfig = RampUpTypeConfig(
    id = 2,
    name = "1",
    minPower = 10.0,
    maxPower = 20.0,
    powerPlantType = PowerPlantType.RampUpType,
    rampRateInSeconds = 2.seconds,
    rampPowerRate = 1.0
  )

  // We assume that we have 2 PowerPlant's in our database
  val testPowerPlantsConfig = PowerPlantsConfig(
    snapshotDateTime = DateTime.now(DateTimeZone.UTC),
    powerPlantConfigSeq = Seq(testOnOffConfig, testRampUpConfig)
  )

  "DBServiceActor#toEvents" must {

    // tests to test the DBServiceActor companion
    "populate update events when an update happens in the database" in {
      val oldCfg = testPowerPlantsConfig
      val newCfg = testPowerPlantsConfig.copy(
        snapshotDateTime = DateTime.now(DateTimeZone.UTC),
        powerPlantConfigSeq = Seq(
          // We just update one PowerPlant and we should see this as an event
          testOnOffConfig.copy(maxPower = testOnOffConfig.maxPower + 10.0),
          testRampUpConfig
        )
      )

      val events: Seq[PowerPlantDBEvent[PowerPlantConfig]] = DBServiceActor.toEvents(oldCfg, newCfg)

      // We expect only one event to happen
      assert(events.size === 1)

      // This event should be of the type PowerPlantUpdateEvent[OnOffTypeConfig]
      assert(events.head.isInstanceOf[PowerPlantUpdateEvent[_]])
      assert(events.head.powerPlantCfg.id === testOnOffConfig.id)
      assert(events.head.powerPlantCfg.maxPower === testOnOffConfig.maxPower + 10.0)
    }

    "populate delete events when a delete happens in the database" in {
      val oldCfg = testPowerPlantsConfig
      val newCfg = testPowerPlantsConfig.copy(
        snapshotDateTime = DateTime.now(DateTimeZone.UTC),
        powerPlantConfigSeq = Seq( // We delete the RampUpType PowerPlant in the database
          testOnOffConfig
        )
      )

      val events = DBServiceActor.toEvents(oldCfg, newCfg)

      // We expect only one event to happen
      assert(events.size === 1)

      // This event should be of the type PowerPlantDeleteEvent[RampUpTypeConfig]
      assert(events.head.isInstanceOf[PowerPlantDeleteEvent[_]])
      assert(events.head.powerPlantCfg.id === testRampUpConfig.id)
    }

    "populate create events when a create happens in the database" in {
      val oldCfg = testPowerPlantsConfig
      val newCfg = testPowerPlantsConfig.copy(
        snapshotDateTime = DateTime.now(DateTimeZone.UTC),
        powerPlantConfigSeq = Seq(
          testOnOffConfig,
          testRampUpConfig,
          testOnOffConfig.copy(id = 3, maxPower = 30000),
          testRampUpConfig.copy(id = 4, minPower = 100000)
        )
      )

      val events = DBServiceActor.toEvents(oldCfg, newCfg)

      // We expect two events to happen as we added 2 new PowerPlant's
      assert(events.size === 2)
      events.foreach(event => assert(event.isInstanceOf[PowerPlantCreateEvent[_]]))

      events.foreach {
        // One of the event is of type PowerPlantCreateEvent[OnOffTypeConfig]
        case event if event.powerPlantCfg.powerPlantType == PowerPlantType.OnOffType =>
          assert(event.powerPlantCfg.id === 3)
          assert(event.powerPlantCfg.maxPower === 30000)

        // One of the event is of type PowerPlantCreateEvent[RampUpTypeConfig]
        case event if event.powerPlantCfg.powerPlantType == PowerPlantType.RampUpType =>
          assert(event.powerPlantCfg.id === 4)
          assert(event.powerPlantCfg.minPower === 100000)

        case _ => fail("Was expected PowerPlantCreateEvent event but an unexpected event was triggered")
      }
    }

    "populate create / update / delete events when create / update / delete" +
      " happens in the database" in {
      val oldCfg = testPowerPlantsConfig
      val newCfg = testPowerPlantsConfig.copy(
        snapshotDateTime = DateTime.now(DateTimeZone.UTC),
        powerPlantConfigSeq = Seq(
          // We update this in the database
          testOnOffConfig.copy(maxPower = testOnOffConfig.maxPower + 10.0),
          //testRampUpConfig, // We delete this from the database
          // We add two new entries in the database
          testOnOffConfig.copy(id = 3, maxPower = 30000),
          testRampUpConfig.copy(id = 4, minPower = 100000)
        )
      )

      val events = DBServiceActor.toEvents(oldCfg, newCfg)

      // We expect 4 events to happen as we added 2 new PowerPlant's, update one and deleted one
      assert(events.size === 4)

      // 1. Check for PowerPlantCreateEvent events
      val createEvents = events.collect {
        case event if event.isInstanceOf[PowerPlantCreateEvent[_]] => event
      }
      assert(createEvents.size === 2)
      createEvents.foreach {
        // One of the event is of type PowerPlantCreateEvent[OnOffTypeConfig]
        case event if event.powerPlantCfg.powerPlantType == PowerPlantType.OnOffType =>
          assert(event.powerPlantCfg.id === 3)
          assert(event.powerPlantCfg.maxPower === 30000)

        // One of the event is of type PowerPlantCreateEvent[RampUpTypeConfig]
        case event if event.powerPlantCfg.powerPlantType == PowerPlantType.RampUpType =>
          assert(event.powerPlantCfg.id === 4)
          assert(event.powerPlantCfg.minPower === 100000)

        case _ => fail("Was expected PowerPlantCreateEvent event but an unexpected event was triggered")
      }

      // 2. Check for PowerPlantUpdateEvent events
      val updateEvents = events.collect {
        case event if event.isInstanceOf[PowerPlantUpdateEvent[_]] => event
      }
      assert(updateEvents.size === 1)
      updateEvents.foreach {
        case event if event.isInstanceOf[PowerPlantUpdateEvent[_]]=>
          assert(event.powerPlantCfg.id === testOnOffConfig.id)
          assert(event.powerPlantCfg.maxPower === testOnOffConfig.maxPower + 10.0)

        case _ => fail("Was expected PowerPlantUpdateEvent event but an unexpected event was triggered")
      }

      // 3. Check for PowerPlantDeleteEvent events
      val deleteEvents = events.collect {
        case event if event.isInstanceOf[PowerPlantDeleteEvent[_]] => event
      }
      assert(deleteEvents.size === 1)
      deleteEvents.foreach {
        // Check for PowerPlantDeleteEvent event
        case event if event.isInstanceOf[PowerPlantDeleteEvent[_]]=>
          assert(event.powerPlantCfg.id === testRampUpConfig.id)
          assert(event.powerPlantCfg.maxPower === testRampUpConfig.maxPower)

        case _ => fail("Was expected PowerPlantDeleteEvent event but an unexpected event was triggered")
      }
    }
  }

  "DBServiceActor" must {

    // This message is used to fetch and check the received events in the TestSupervisorActorRef
    case object GetReceivedEvents

    // Our Test Actor that will receive events from the DBServiceActor which is being tested here
    class TestSupervisorActorRef extends Actor {

      override def preStart(): Unit = {
        super.preStart()
        context.become(
          active(SupervisorEvents(Seq.empty[PowerPlantDBEvent[PowerPlantConfig]]))
        )
      }

      override def receive: Receive = {
        case _ => logger.info("Nothing to receive")
      }

      def active(supervisorEvents: SupervisorEvents): Receive = {
        case GetReceivedEvents =>
          sender ! supervisorEvents

        case newEvents @ SupervisorEvents(_) =>
          // When we get new events, we update the call stack
          context.become(active(newEvents))
      }
    }

    // Get a reference to our TestSupervisorActor
    val testSupervisorActorRef = system.actorOf(Props(new TestSupervisorActorRef), "test-supervisor-actor")

    // We disable the Observable inside the DBServiceActor, so that it is easy to unit test!
    val dbServiceActor = system.actorOf(
      DBServiceActor.props(
        AppConfig.load().dbConfig,
        testSupervisorActorRef
      )
    )

    "populate PowerPlantsConfig upon every message it receives" in {
      // This will be our service instance
      val powerPlantRepo = new PowerPlantRepoAsTask(config.dbConfig)
      val powerPlantService = new PowerPlantService(powerPlantRepo)

      // Let us start initially with the available PowerPlant entries in the database
      val allActivePowerPlants = Await.result(powerPlantService.fetchAllPowerPlants(onlyActive = true).runAsync, 3.seconds)

      // Now send this initial Seq of PowerPlant's to the dbServiceActor (transformed as a PowerPlantConfig type)
      within(2.seconds) {
        dbServiceActor ! com.inland24.plantsim.models.toPowerPlantsConfig(allActivePowerPlants)
        expectNoMsg()
      }

      // The dbServiceActor should have send those messages to our TestSupervisorActor instance, check it
      val supEvents1 = Await.result((testSupervisorActorRef ? GetReceivedEvents).mapTo[SupervisorEvents], 3.seconds)
      assert(
        supEvents1.events.length ===  allActivePowerPlants.length,
        s"unexpected number of events received by the TestSupervisorActor, " +
          s"was expecting ${allActivePowerPlants.length} events but got ${supEvents1.events.length} events"
      )

      // So far so good! Now let us assume that we removed all PowerPlant's with RampUpType from the database
      val allOnOffTypePlants = allActivePowerPlants.filter(_.powerPlantTyp == OnOffType)
      val updatedPowerPlant = allOnOffTypePlants.head.copy(orgName = "Joesan updated the name")

      // And updated the PowerPlant with id == 1
      val allOnOffPlantsUpdated = allOnOffTypePlants.map {
        case powerPlant if updatedPowerPlant.id == powerPlant.id =>
          updatedPowerPlant
        case powerPlant =>
          powerPlant
      }

      // And then send this update to the dbServiceActor
      within(3.seconds) {
        dbServiceActor ! com.inland24.plantsim.models.toPowerPlantsConfig(allOnOffPlantsUpdated)
        expectNoMsg()
      }

      /*
       * Now let us check if we have received the following events it the TestSupervisorActor
       *
       * PowerPlantUpdateEvent - 1 event expected for PowerPlant with id = 1
       * PowerPlantDeleteEvent - 3 event's expected for all RampUpType PowerPlant's
       *
       * So a total of 4 events are expected
       */
      val supEvents2 = Await.result((testSupervisorActorRef ? GetReceivedEvents).mapTo[SupervisorEvents], 3.seconds)
      assert(
        supEvents2.events.length ===  4,
        s"unexpected number of events received by the TestSupervisorActor, " +
          s"was expecting 4 events but got ${supEvents2.events.length} events"
      )
    }
  }
}