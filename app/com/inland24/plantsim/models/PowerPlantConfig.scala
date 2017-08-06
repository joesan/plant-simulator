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

import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.duration.FiniteDuration


sealed trait PowerPlantConfig {
  def id: Long
  def name: String
  def minPower: Double
  def maxPower: Double
  def powerPlantType: PowerPlantType
}
object PowerPlantConfig {

  case class OnOffTypeConfig(
    id: Long,
    name: String,
    minPower: Double,
    maxPower: Double,
    powerPlantType: PowerPlantType
  ) extends PowerPlantConfig

  case class RampUpTypeConfig(
    id: Long,
    name: String,
    minPower: Double,
    maxPower: Double,
    rampPowerRate: Double,
    rampRateInSeconds: FiniteDuration,
    powerPlantType: PowerPlantType
  ) extends PowerPlantConfig

  case class UnknownConfig(
    id: Long,
    name: String,
    minPower: Double,
    maxPower: Double,
    powerPlantType: PowerPlantType
  ) extends PowerPlantConfig

  // represents all the PowerPlant's from the database
  case class PowerPlantsConfig(
    snapshotDateTime: DateTime,
    powerPlantConfigSeq: Seq[PowerPlantConfig]
  )
}