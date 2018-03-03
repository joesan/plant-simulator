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
import com.inland24.plantsim.models.PowerPlantType.OnOffType
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.repository.impl.PowerPlantRepoAsTask
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll}
import monix.execution.Scheduler.Implicits.global

// ***** NOTE: Do not remove this import! It won't compile without this
import monix.cats._
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
    powerPlantService.fetchAllPowerPlants().runAsync.map { allPowerPlants =>
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

  "powerPlantsPaginated" should "fetch all PowerPlant's that metch the search criteria" in {
    val searchFilter1 = PowerPlantFilter(
      onlyActive = Some(true),
      powerPlantType = None
    )
    powerPlantService.searchPowerPlants(searchFilter1).runAsync.map {
      filtered =>
        assert(filtered.length === 5)
    }

    val searchFilter2 = PowerPlantFilter(
      onlyActive = Some(true),
      orgName = Some("joesan 1")
    )
    powerPlantService.searchPowerPlants(searchFilter2).runAsync.map {
      filtered =>
        assert(filtered.length === 1)
        assert(filtered.head.id === 1)
    }

    val searchFilter3 = PowerPlantFilter(
      onlyActive = Some(true),
      // Notice the capital letter, there is no name with capital letter in the database
      orgName = Some("Joesan 1")
    )
    powerPlantService.searchPowerPlants(searchFilter3).runAsync.map {
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
    powerPlantService.searchPowerPlants(searchFilter4).runAsync.map {
      filtered =>
        assert(filtered.length === 0)
    }

    val searchFilter5 = PowerPlantFilter(
      onlyActive = Some(true),
      orgName = Some("joesan")
    )
    powerPlantService.searchPowerPlants(searchFilter5).runAsync.map {
      filtered =>
        assert(filtered.length === 5)
    }

    val searchFilter6 = PowerPlantFilter(
      onlyActive = Some(false),
      orgName = Some("joesan")
    )
    powerPlantService.searchPowerPlants(searchFilter6).runAsync.map {
      filtered =>
        assert(filtered.length === 0)
    }
  }

  "allPowerPlantsPaginated" should "fetch all PowerPlant's from the database for the given pageNumber" in {
    powerPlantService
      .searchPowerPlants(PowerPlantFilter())
      .runAsync
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
    powerPlantService.powerPlantById(105).runAsync.flatMap {
      case Some(powerPlant) =>
        assert(powerPlant.id === Some(105))
        assert(powerPlant.orgName === "joesan 5")
        assert(powerPlant.isActive)

      case _ =>
        fail(
          "expected the powerPlant with id 105 but was not found in the database")
    }
  }

  "newPowerPlant" should "add a new PowerPlant to the PowerPlant table" in {
    val newPowerPlantRow = PowerPlantRow(
      id = Some(10000),
      orgName = s"joesan_10000",
      isActive = true,
      minPower = 100.0,
      maxPower = 400.0,
      powerPlantTyp = OnOffType,
      createdAt = getNowAsDateTime(),
      updatedAt = getNowAsDateTime()
    )

    powerPlantService.createNewPowerPlant(newPowerPlantRow).runAsync.flatMap {
      _ =>
        powerPlantService.powerPlantById(10000).runAsync.flatMap {
          case Some(powerPlant) =>
            assert(powerPlant.id === Some(10000))
            assert(powerPlant.isActive)

          case _ =>
            fail("expected the powerPlant with id 10000 but was not found in the database")
        }
    }
  }
}
