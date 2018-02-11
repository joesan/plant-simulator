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

import com.inland24.plantsim.config.DBConfig
import com.inland24.plantsim.models.PowerPlantFilter
import com.inland24.plantsim.services.database.models.PowerPlantRow
import monix.eval.Task
import scala.language.higherKinds


import scala.concurrent.ExecutionContext
// This trait could work on any implementation
trait DBService[M[_]] {
  def allPowerPlants(fetchOnlyActive: Boolean): M[Seq[PowerPlantRow]]
  def powerPlantsPaginated(filter: PowerPlantFilter): M[Seq[PowerPlantRow]]
  def allPowerPlantsPaginated(fetchOnlyActive: Boolean, pageNumber: Int): M[Seq[PowerPlantRow]]
  def powerPlantById(id: Int): M[Option[PowerPlantRow]]
  def newPowerPlant(powerPlantRow: PowerPlantRow): M[Int]
}

class DBServiceTask (dbConfig: DBConfig)(implicit ec: ExecutionContext) extends DBService[Task] { self =>

  private val schema = DBSchema(dbConfig.slickDriver)
  private val database = dbConfig.database
  private val recordsPerPage = dbConfig.recordCountPerPage

  /** Note: These imports should be here! Do not move it */
  import schema._
  import schema.driver.api._

  def allPowerPlants(fetchOnlyActive: Boolean = false): Task[Seq[PowerPlantRow]] = {
    val query =
      if (fetchOnlyActive)
        PowerPlantTable.activePowerPlants
      else
        PowerPlantTable.all

    Task.deferFuture(database.run(query.result))
  }

  def offset(pageNumber: Int): (Int, Int) =
    (pageNumber * recordsPerPage - recordsPerPage, pageNumber * recordsPerPage)

  // fetch the PowerPlants based on the Search criteria
  def powerPlantsPaginated(filter: PowerPlantFilter): Task[Seq[PowerPlantRow]] = {
    val (from, to) = offset(filter.pageNumber)
    val query = PowerPlantTable.powerPlantsFor(filter.powerPlantType, filter.orgName, filter.onlyActive)
    Task.deferFuture(database.run(query.drop(from).take(to).result))
  }

  def applySchedules = ???

  // by default, get the first page!
  def allPowerPlantsPaginated(fetchOnlyActive: Boolean = false, pageNumber: Int = 1): Task[Seq[PowerPlantRow]] = {
    val query =
      if (fetchOnlyActive)
        PowerPlantTable.activePowerPlants
      else
        PowerPlantTable.all

    val (from, to) = offset(pageNumber)
    Task.deferFuture(database.run(query.drop(from).take(to).result))
  }

  def powerPlantById(id: Int): Task[Option[PowerPlantRow]] = {
    println(database.run(PowerPlantTable.powerPlantById(id).result.headOption))
    Task.deferFuture(database.run(PowerPlantTable.powerPlantById(id).result.headOption))
  }

  def newPowerPlant(powerPlantRow: PowerPlantRow): Task[Int] = {
    Task.deferFuture(database.run(PowerPlantTable.all += powerPlantRow))
  }
}
object DBService {

  def asTask(dbCfg: DBConfig)(implicit ec: ExecutionContext) =
    new DBServiceTask(dbCfg)(ec)
}