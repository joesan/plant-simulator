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

package com.inland24.plantsim.services.database.repository

import com.inland24.plantsim.models.PowerPlantFilter
import com.inland24.plantsim.services.database.models.PowerPlantRow
import scala.language.higherKinds


trait PowerPlantRepository[M[_]] {
  def allPowerPlants(fetchOnlyActive: Boolean): M[Seq[PowerPlantRow]]
  def powerPlantsPaginated(filter: PowerPlantFilter): M[Seq[PowerPlantRow]]
  def allPowerPlantsPaginated(fetchOnlyActive: Boolean, pageNumber: Int): M[Seq[PowerPlantRow]]
  def powerPlantById(id: Int): M[Option[PowerPlantRow]]
  def newPowerPlant(powerPlantRow: PowerPlantRow): M[Int]
  def updatePowerPlant(powerPlantRow: PowerPlantRow): M[PowerPlantRow]
}