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

package com.inland24.plantsim.models

// Trait representing the occurrence of events for a PowerPlant in the database
sealed trait PowerPlantDBEvent[T <: PowerPlantConfig] {
  def id: Long
  def powerPlantCfg: T
}

object PowerPlantDBEvent {

  // A PowerPlant could be updated in the database
  final case class PowerPlantUpdateEvent[T <: PowerPlantConfig](
      id: Long,
      powerPlantCfg: T
  ) extends PowerPlantDBEvent[T]

  // A PowerPlant could be newly created in the database
  final case class PowerPlantCreateEvent[T <: PowerPlantConfig](
      id: Long,
      powerPlantCfg: T
  ) extends PowerPlantDBEvent[T]

  // A PowerPlant could be deleted in the database
  final case class PowerPlantDeleteEvent[T <: PowerPlantConfig](
      id: Long,
      powerPlantCfg: T
  ) extends PowerPlantDBEvent[T]
}
