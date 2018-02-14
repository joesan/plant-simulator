/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inland24.plantsim.services.database

import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType}
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.repository.DBSchema
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.Await
import scala.concurrent.duration._


// The base class that contains the H2 database with some pre-populated rows
trait DBServiceSpec {

  def getNowAsDateTime(): DateTime = {
    DateTime.now(DateTimeZone.UTC)
  }

  // let's use test configurations
  System.setProperty("ENV", "test")

  val config = AppConfig.load()
  val testDatabase = config.dbConfig.database

  // initialize the db service
  val dbSchema = DBSchema(config.dbConfig.slickDriver)

  /* This shitty import should be here - Do not remove */
  import dbSchema._
  import dbSchema.driver.api._

  val powerPlants = (1 to 6) map {
    i =>
      PowerPlantRow(
        id = Some(100 + i),
        orgName = s"joesan $i",
        isActive = true,
        minPower = 100.0 * i,
        maxPower = 400.0 * 2 * i,
        powerPlantTyp = if (i % 2 == 0) OnOffType else RampUpType,
        rampRatePower = if (i % 2 == 0) None else Some(20.0),
        rampRateSecs = if (i % 2 == 0)  None else Some(2),
        createdAt = getNowAsDateTime(),
        updatedAt = getNowAsDateTime()
      )
  }

  protected def h2SchemaDrop() = {
    val schema = DBIO.seq(
      //(AddressTable.all.schema ++ PowerPlantTable.all.schema).drop
      PowerPlantTable.all.schema.drop
    )
    Await.result(testDatabase.run(schema), 5.seconds)
  }

  protected def h2SchemaSetup() = {
    val schema = DBIO.seq(
      //(AddressTable.all.schema ++ PowerPlantTable.all.schema).create
      PowerPlantTable.all.schema.create
    )
    Await.result(testDatabase.run(schema), 5.seconds)
  }

  protected def populateTables() = {
    val setup = DBIO.seq(
      // Insert some addresses
      //AddressTable.all ++= addresses,

      // Insert some power plants
      PowerPlantTable.all ++= powerPlants
    )
    Await.result(testDatabase.run(setup), 5.seconds)
  }
}