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

import com.inland24.plantsim.models.{PowerPlantFilter, PowerPlantType}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.repository.impl.PowerPlantRepoAsTask
import org.scalatest.BeforeAndAfterAll
import monix.execution.Scheduler.Implicits.global
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

// ***** NOTE: Do not remove this import! It won't compile without this
import cats._
// *****

final class PowerPlantDBServiceSpec
    extends AsyncFlatSpec
    with DBServiceSpec
    with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    // 1. Set up the Schemas
    super.h2SchemaSetup()

    // 2. Populate the tables
    super.populateTables()
  }

  override def afterAll(): Unit = {
    super.h2SchemaDrop()
  }

  // This will be our service instance
  val powerPlantRepo = new PowerPlantRepoAsTask(config.dbConfig)
  val powerPlantService = new PowerPlantService(powerPlantRepo)

  behavior of "PowerPlantDBService"

  "allPowerPlants" should "fetch all PowerPlant's from the database" in {
    powerPlantService.fetchAllPowerPlants().runToFuture.map { allPowerPlants =>
      assert(allPowerPlants.length === 6)

      allPowerPlants.headOption match {
        case Some(powerPlant1) =>
          assert(powerPlant1.id === Some(101))
          assert(powerPlant1.orgName === "joesan 1")
          assert(powerPlant1.isActive)
          assert(powerPlant1.powerPlantTyp === PowerPlantType.RampUpType)
        case None =>
          fail(
            "expected a PowerPlant with id 101 in the database, but not found")
      }
    }
  }

  "powerPlantsPaginated" should "fetch all PowerPlant's that match the search criteria" in {
    val searchFilter1 = PowerPlantFilter(
      onlyActive = Some(true),
      powerPlantType = None
    )
    powerPlantService.searchPowerPlants(searchFilter1).runToFuture.map {
      filtered =>
        assert(filtered.length === 5)
    }

    val searchFilter2 = PowerPlantFilter(
      onlyActive = Some(true),
      orgName = Some("joesan 1")
    )
    powerPlantService.searchPowerPlants(searchFilter2).runToFuture.map {
      filtered =>
        assert(filtered.length === 1)
        assert(filtered.head.id === 1)
    }

    val searchFilter3 = PowerPlantFilter(
      onlyActive = Some(true),
      // Notice the capital letter, there is no name with capital letter in the database
      orgName = Some("Joesan 1")
    )
    powerPlantService.searchPowerPlants(searchFilter3).runToFuture.map {
      filtered =>
        assert(filtered.length === 0)
    }

    val searchFilter4 = PowerPlantFilter(
      onlyActive = Some(true),
      // There is only one element which will return when pageNumber is 1,
      // but nothing is there on pageNumber 20
      pageNumber = 20,
      orgName = Some("joesan 1")
    )
    powerPlantService.searchPowerPlants(searchFilter4).runToFuture.map {
      filtered =>
        assert(filtered.length === 0)
    }

    val searchFilter5 = PowerPlantFilter(
      onlyActive = Some(true),
      orgName = Some("joesan")
    )
    powerPlantService.searchPowerPlants(searchFilter5).runToFuture.map {
      filtered =>
        assert(filtered.length === 5)
    }

    val searchFilter6 = PowerPlantFilter(
      onlyActive = Some(false),
      orgName = Some("joesan")
    )
    powerPlantService.searchPowerPlants(searchFilter6).runToFuture.map {
      filtered =>
        assert(filtered.length === 0)
    }
  }

  "allPowerPlantsPaginated" should "fetch all PowerPlant's from the database for the given pageNumber" in {
    powerPlantService
      .searchPowerPlants(PowerPlantFilter())
      .runToFuture
      .map { // by default we ask for the first page
        allPowerPlants =>
          assert(allPowerPlants.length === 5)

          allPowerPlants.headOption match {
            case Some(powerPlant1) =>
              assert(powerPlant1.id === Some(101))
              assert(powerPlant1.orgName === "joesan 1")
              assert(powerPlant1.isActive)
              assert(powerPlant1.powerPlantTyp === PowerPlantType.RampUpType)
            case None =>
              fail(
                "expected a PowerPlant with id 101 in the database, but not found")
          }
      }
  }

  "powerPlantById" should "fetch the PowerPlant for the given id" in {
    powerPlantService.powerPlantById(105).runToFuture.flatMap {
      case Some(powerPlant) =>
        assert(powerPlant.id === Some(105))
        assert(powerPlant.orgName === "joesan 5")
        assert(powerPlant.isActive)

      case _ =>
        fail(
          "expected the powerPlant with id 105 but was not found in the database")
    }
  }

  // Create PowerPlant tests
  "newPowerPlant" should "add a new PowerPlant to the PowerPlant table" in {
    val newPowerPlantRow = PowerPlantRow(
      id = Some(10000),
      orgName = s"joesan_10000",
      isActive = true,
      minPower = 100.0,
      maxPower = 400.0,
      powerPlantTyp = OnOffType,
      createdAt = getNowAsDateTime,
      updatedAt = getNowAsDateTime
    )

    powerPlantService
      .createNewPowerPlant(newPowerPlantRow)
      .runToFuture
      .flatMap { _ =>
        powerPlantService.powerPlantById(10000).runToFuture.flatMap {
          case Some(powerPlant) =>
            assert(powerPlant.id === Some(10000))
            assert(powerPlant.isActive)

          case _ =>
            fail("expected the powerPlant with id 10000 but was not found in the database")
        }
      }
  }

  "newPowerPlant" should "not create a new PowerPlant for an already existing PowerPlant" in {
    // PowerPlant with id = 101 already exists in the database
    val newPowerPlantRow = PowerPlantRow(
      id = Some(101),
      orgName = "joesan 101",
      isActive = true,
      minPower = 100.0,
      maxPower = 800.0,
      powerPlantTyp = RampUpType,
      rampRatePower = Some(20.0),
      rampRateSecs = Some(2),
      createdAt = getNowAsDateTime,
      updatedAt = getNowAsDateTime
    )

    // We expect a unique key violation error from the database
    val retVal = Try(
      Await.result(
        powerPlantService.createNewPowerPlant(newPowerPlantRow).runToFuture,
        2.seconds))
    retVal match {
      case Failure(fail) =>
        assert(
          fail.getMessage.contains("Unique index or primary key violation"))
      case Success(_) =>
        fail(
          "Expected the new creation of an already existing PowerPlant to fail, " +
            "but is succeeded! This is serious error and should be analyzed")
    }
  }

  // Update PowerPlant tests
  "updatePowerPlant" should "update an existing PowerPlant to the PowerPlant table" in {
    // PowerPlant with id = 101 already exists in the database, so we should be able to update it
    val updatePowerPlantRow = PowerPlantRow(
      id = Some(101),
      orgName = "joesan 101 updated", // We update the name
      isActive = true,
      minPower = 100.0,
      maxPower = 800.0,
      powerPlantTyp = RampUpType,
      rampRatePower = Some(20.0),
      rampRateSecs = Some(2),
      createdAt = getNowAsDateTime,
      updatedAt = getNowAsDateTime
    )

    powerPlantService
      .updatePowerPlant(updatePowerPlantRow)
      .runToFuture
      .flatMap { _ =>
        powerPlantService.powerPlantById(101).runToFuture.flatMap {
          case Some(powerPlant) =>
            assert(powerPlant.id === Some(101))
            assert(powerPlant.orgName === "joesan 101 updated")
            assert(powerPlant.isActive)

          case _ =>
            fail("expected the powerPlant with id 101 but was not found in the database")
        }
      }
  }

  "updatePowerPlant" should "not update a PowerPlant that does not exist in the database" in {
    // PowerPlant with id = 101 already exists in the database, so we should be able to update it
    val updatePowerPlantRow = PowerPlantRow(
      id = Some(25000), // This does not exist
      orgName = "joesan 25000", // We update the name
      isActive = true,
      minPower = 100.0,
      maxPower = 800.0,
      powerPlantTyp = RampUpType,
      rampRatePower = Some(20.0),
      rampRateSecs = Some(2),
      createdAt = getNowAsDateTime,
      updatedAt = getNowAsDateTime
    )

    val elem = Await.result(
      powerPlantService.updatePowerPlant(updatePowerPlantRow).runToFuture,
      2.seconds)
    assert(elem.isLeft)
    assert(
      elem.left
        .getOrElse("") === "PowerPlant not found for the given id 25000")
  }
}
