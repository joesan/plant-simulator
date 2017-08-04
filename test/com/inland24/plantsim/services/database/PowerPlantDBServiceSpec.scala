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

import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll}


final class PowerPlantDBServiceSpec extends AsyncFlatSpec
  with PowerPlantDBServiceSpec with BeforeAndAfterAll {

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
  val powerPlantDBService = new DBService(config.database)

  behavior of "PowerPlantDBService"

  "allPowerPlants" should "fetch all PowerPlant's from the database" in {
    powerPlantDBService.allPowerPlants().map {
      allPowerPlants =>
        assert(allPowerPlants.length === 6)

        allPowerPlants.headOption match {
          case Some(powerPlant1) =>
            assert(powerPlant1.id === 101)
            assert(powerPlant1.orgName === "joesan 1")
            assert(powerPlant1.isActive)
            assert(powerPlant1.powerPlantTyp === PowerPlantType.RampUpType)
          case None =>
            fail("expected a PowerPlant with id 101 in the database, but not found")
        }
    }
  }

  "powerPlantById" should "fetch the PowerPlant for the given id" in {
    powerPlantDBService.powerPlantById(105).flatMap {
      case Some(powerPlant) =>
        assert(powerPlant.id === 105)
        assert(powerPlant.orgName === "joesan 5")
        assert(powerPlant.isActive)

      case _ => fail("expected the powerPlant with id 105 but was not found in the database")
    }
  }

  "inactivatePowerPlant" should "deactivate a PowerPlant for a given id" in {
    powerPlantDBService.inActivatePowerPlant(106).map {
      updateCount =>
        assert(updateCount === 1)
    }

    // check if the powerPlant is really in activated
    powerPlantDBService.powerPlantById(106).flatMap {
      case Some(powerPlant) =>
        assert(powerPlant.id === 106)
        assert(!powerPlant.isActive)

      case _ => fail("expected the PowerPlant with id 106 to be inActive, but was active")
    }
  }

  "allPowerPlants" should "fetch only active PowerPlant's when setting the flag to true" in {
    // let's first deactivate one!
    powerPlantDBService.inActivatePowerPlant(106).map {
      updateCount =>
        assert(updateCount === 1)
    }

    powerPlantDBService.allPowerPlants(fetchOnlyActive = true).map {
      allPowerPlants =>
        assert(allPowerPlants.length === 5)

        allPowerPlants.lastOption match {
          case Some(powerPlant5) =>
            assert(powerPlant5.id === 105)
            assert(powerPlant5.orgName === "joesan 5")
            assert(powerPlant5.isActive)
            assert(powerPlant5.powerPlantTyp === PowerPlantType.RampUpType)
          case _ =>
            fail("expected a PowerPlant with id = 105 from the database, but not found")
        }
    }
  }
}