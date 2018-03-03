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

package com.inland24.plantsim

import java.util.concurrent.TimeUnit

import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.models.PowerPlantConfig._
import com.inland24.plantsim.models.PowerPlantSignal.{DispatchAlert, Genesis, Transition}
import com.inland24.plantsim.models.PowerPlantType._
import com.inland24.plantsim.services.database.models.PowerPlantRow
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.JodaWrites._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration


package object models {

  implicit val appConfigWrites = new Writes[AppConfig] {
    def writes(appConfig: AppConfig) = Json.obj(
      "environment" -> appConfig.environment,
      "application" -> appConfig.appName,
      "dbConfig" -> Json.obj(
        "databaseDriver" -> appConfig.dbConfig.driver,
        "databaseUrl" -> appConfig.dbConfig.url,
        "databaseUser" -> "***********",
        "databasePass" -> "***********"
      )
    )
  }

  implicit val finiteDurationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {
    def reads(json: JsValue): JsResult[FiniteDuration] = {
      LongReads.reads(json).map(_.seconds)
    }

    def writes(f: FiniteDuration): JsValue = {
      JsString(f.toString())
    }
  }

  implicit val powerPlantCfgFormat: Format[PowerPlantConfig] = new Format[PowerPlantConfig] {
    def reads(json: JsValue): JsResult[PowerPlantConfig] = {
      val powerPlantTyp = PowerPlantType.fromString((json \ "powerPlantType").as[String])
      powerPlantTyp match {
        case PowerPlantType.OnOffType =>
          for {
            powerPlantId <- (json \ "powerPlantId").validate[Int]
            name <- (json \ "powerPlantName").validate[String]
            minPower <- (json \ "minPower").validate[Double]
            maxPower <- (json \ "maxPower").validate[Double]
          } yield {
            OnOffTypeConfig(
              id = powerPlantId,
              name = name,
              minPower = minPower,
              maxPower = maxPower,
              powerPlantType = powerPlantTyp
            )
          }
        case PowerPlantType.RampUpType =>
          for {
            powerPlantId <- (json \ "powerPlantId").validate[Int]
            name <- (json \ "powerPlantName").validate[String]
            minPower <- (json \ "minPower").validate[Double]
            maxPower <- (json \ "maxPower").validate[Double]
            rampPowerRate <- (json \ "rampPowerRate").validate[Double]
            rampRateInSeconds <- (json \ "rampRateInSeconds").validate[FiniteDuration]
          } yield {
            RampUpTypeConfig(
              id = powerPlantId,
              name = name,
              minPower = minPower,
              rampPowerRate = rampPowerRate,
              rampRateInSeconds = rampRateInSeconds,
              maxPower = maxPower,
              powerPlantType = powerPlantTyp
            )
          }
        case _ =>
          JsError(__ \ "powerPlantType",
            s"Invalid PowerPlantType $powerPlantTyp. Should be one of RampUpType or OnOffType"
          )
      }
    }

    def writes(o: PowerPlantConfig): JsValue = {
      if (o.powerPlantType == RampUpType) {
        Json.obj(
          "powerPlantId" -> o.id,
          "powerPlantName" -> o.name,
          "minPower" -> o.minPower,
          "maxPower" -> o.maxPower,
          "rampPowerRate" -> o.asInstanceOf[RampUpTypeConfig].rampPowerRate,
          "rampRateInSeconds" -> o.asInstanceOf[RampUpTypeConfig].rampRateInSeconds.length,
          "powerPlantType" -> PowerPlantType.toString(o.powerPlantType)
        )
      }
      else {
        Json.obj(
          "powerPlantId" -> o.id,
          "powerPlantName" -> o.name,
          "minPower" -> o.minPower,
          "maxPower" -> o.maxPower,
          "powerPlantType" -> PowerPlantType.toString(o.powerPlantType)
        )
      }
    }
  }

  implicit def toPowerPlantRow(cfg: PowerPlantConfig): Option[PowerPlantRow] = cfg.powerPlantType match {
    case OnOffType =>
      Some(PowerPlantRow(
        id = Some(cfg.id),
        orgName = cfg.name,
        isActive = true,
        minPower = cfg.minPower,
        powerPlantTyp = OnOffType,
        maxPower = cfg.maxPower,
        rampRatePower = None,
        rampRateSecs = None,
        createdAt = DateTime.now(DateTimeZone.UTC),
        updatedAt = DateTime.now(DateTimeZone.UTC)
      ))
    case RampUpType =>
      Some(PowerPlantRow(
        id = Some(cfg.id),
        orgName = cfg.name,
        isActive = true,
        minPower = cfg.minPower,
        powerPlantTyp = OnOffType,
        maxPower = cfg.maxPower,
        rampRatePower = Some(cfg.asInstanceOf[RampUpTypeConfig].rampPowerRate),
        rampRateSecs = Some(cfg.asInstanceOf[RampUpTypeConfig].rampRateInSeconds.toSeconds),
        createdAt = DateTime.now(DateTimeZone.UTC),
        updatedAt = DateTime.now(DateTimeZone.UTC)
      ))
    case _ => // TODO Log
      None
  }

  // implicit conversion from database row types to model types
  implicit def toPowerPlantsConfig(seqPowerPlantRow: Seq[PowerPlantRow]): PowerPlantsConfig = {
    PowerPlantsConfig(
      DateTime.now(DateTimeZone.UTC),
      seqPowerPlantRow.map(toPowerPlantConfig)
    )
  }

  implicit def toPowerPlantConfig(powerPlantRow: PowerPlantRow): PowerPlantConfig = {
    powerPlantRow.id match {
      case Some(powerPlantId) =>
        powerPlantRow.powerPlantTyp match {
          case OnOffType =>
            OnOffTypeConfig(
              id = powerPlantId,
              name = powerPlantRow.orgName,
              minPower = powerPlantRow.minPower,
              maxPower = powerPlantRow.maxPower,
              powerPlantType = OnOffType
            )
          case RampUpType
            if powerPlantRow.rampRatePower.isDefined && powerPlantRow.rampRateSecs.isDefined =>
            RampUpTypeConfig(
              id = powerPlantId,
              name = powerPlantRow.orgName,
              minPower = powerPlantRow.minPower,
              maxPower = powerPlantRow.maxPower,
              rampPowerRate = powerPlantRow.rampRatePower.get,
              rampRateInSeconds = FiniteDuration(powerPlantRow.rampRateSecs.get, TimeUnit.SECONDS),
              powerPlantType = RampUpType
            )
          // If it is of RampUpType but rampPowerRate and rampRateInSeconds are not specified, we error
          case RampUpType =>
            UnknownConfig(
              id = powerPlantId,
              name = powerPlantRow.orgName,
              minPower = powerPlantRow.minPower,
              maxPower = powerPlantRow.maxPower,
              powerPlantType = UnknownType
            )
        }

      case None =>
        UnknownConfig(
          name = powerPlantRow.orgName,
          minPower = powerPlantRow.minPower,
          maxPower = powerPlantRow.maxPower,
          powerPlantType = UnknownType
        )
    }
  }

  implicit val powerPlantSignalWrites = new Writes[PowerPlantSignal] {
    def writes(powerPlantSignal: PowerPlantSignal) = powerPlantSignal match {
      case Genesis(newState, powerPlantCfg, timeStamp) => Json.obj(
        "newState" -> newState.toString,
        "powerPlantCfg" -> powerPlantCfg,
        "timeStamp" -> timeStamp
      )
      case Transition(oldState, newState, powerPlantCfg, timeStamp) => Json.obj(
        "newState" -> newState.toString,
        "oldState" -> oldState.toString,
        "powerPlantCfg" -> powerPlantCfg,
        "timeStamp" -> timeStamp
      )
      case DispatchAlert(msg, powerPlantCfg, timeStamp) => Json.obj(
        "message" -> msg,
        "powerPlantCfg" -> powerPlantCfg,
        "timeStamp" -> timeStamp
      )
    }
  }
}