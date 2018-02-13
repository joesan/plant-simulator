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

import cats.Monad
import cats.syntax.all._
import com.inland24.plantsim.models.{PowerPlantConfig, toPowerPlantRow}
import com.inland24.plantsim.services.database.models.PowerPlantRow
import com.inland24.plantsim.services.database.repository.PowerPlantRepository

import scala.language.higherKinds


class PowerPlantDBService[M[_]: Monad](powerPlantRepo: PowerPlantRepository[M]) {

  def updatePowerPlant(powerPlantCfg: PowerPlantConfig): M[Either[String, PowerPlantRow]] = {
    powerPlantRepo.powerPlantById(powerPlantCfg.id).flatMap {
      case Some(powerPlantRow) =>
        toPowerPlantRow(powerPlantCfg) match {
          case Some(newPowerPlantRow) =>
            powerPlantRepo.updatePowerPlant(newPowerPlantRow).map(_.asRight)
          case None =>
            implicitly[Monad[M]].pure(Left(s"Invalid $powerPlantRow"))
        }
      case None => implicitly[Monad[M]].pure(Left(s"PowerPlant not found for the given id ${powerPlantCfg.id}"))
    }
  }
}