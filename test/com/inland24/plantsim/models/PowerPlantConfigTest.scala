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

import com.inland24.plantsim.models.PowerPlantConfig.{OnOffTypeConfig, RampUpTypeConfig, UnknownConfig}
import com.inland24.plantsim.models.PowerPlantType.{OnOffType, RampUpType, UnknownType}
import com.inland24.plantsim.services.database.models.PowerPlantRow
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.FlatSpec

import scala.concurrent.Future


class PowerPlantConfigTest extends FlatSpec {

  behavior of "PowerPlantConfig"

  private val testOnOffTypePowerPlantRow = PowerPlantRow(
    id = 1,
    orgName = "1",
    minPower = 10.0,
    maxPower = 20.0,
    isActive = true,
    powerPlantTyp = PowerPlantType.OnOffType,
    createdAt = DateTime.now(DateTimeZone.UTC),
    updatedAt = DateTime.now(DateTimeZone.UTC)
  )

  private val testRampUpPowerPlantRow = PowerPlantRow(
    id = 2,
    orgName = "2",
    minPower = 10.0,
    maxPower = 20.0,
    isActive = true,
    rampRatePower = Some(2),
    rampRateSecs = Some(2),
    powerPlantTyp = PowerPlantType.RampUpType,
    createdAt = DateTime.now(DateTimeZone.UTC),
    updatedAt = DateTime.now(DateTimeZone.UTC)
  )

  "Conversion from PowerPlantRow to PowerPlantConfig" should
    "convert to the appropriate PowerPlantConfig" in {
    val powerPlantCfg = toPowerPlantsConfig(
      Seq(testOnOffTypePowerPlantRow, testRampUpPowerPlantRow)
    )

    // We expect 2 entries in the result
    assert(powerPlantCfg.powerPlantConfigSeq.length === 2)

    import monix.execution.Scheduler.Implicits.global

    def toAdd(elem: Long) = Future { elem + 1 }

    // TODO: Remove this bit of code!!!
    val someFuture = Future { 1 + 1 }.map(elem => {
      val added = toAdd(elem)
      println(added)
      println(elem)
      elem.toString
    })

    import scala.concurrent.duration._
    val obs = Observable.intervalAtFixedRate(2.seconds)
                .flatMap(_ => Observable.fromFuture(someFuture))

    val subs = new Subscriber[String] {
      override def onNext(str: String): Future[Ack] = {
        println(s"in onNext $str")
        Continue
      }

      override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

      override def onError(ex: Throwable): Unit = ex.printStackTrace()

      override def onComplete(): Unit = println("done ****")
    }

    obs.subscribe(subs)

    powerPlantCfg.powerPlantConfigSeq.foreach {
      case cfg if cfg.powerPlantType == RampUpType =>
        assert(cfg.isInstanceOf[RampUpTypeConfig])
        assert(cfg.id === testRampUpPowerPlantRow.id)
        assert(cfg.maxPower === testRampUpPowerPlantRow.maxPower)
        assert(cfg.minPower === testRampUpPowerPlantRow.minPower)
        assert(cfg.name === testRampUpPowerPlantRow.orgName)
        assert(
          cfg.asInstanceOf[RampUpTypeConfig].rampRateInSeconds.toSeconds === testRampUpPowerPlantRow.rampRateSecs.get
        )
        assert(
          cfg.asInstanceOf[RampUpTypeConfig].rampPowerRate === testRampUpPowerPlantRow.rampRatePower.get
        )

      case cfg if cfg.powerPlantType == OnOffType =>
        assert(cfg.isInstanceOf[OnOffTypeConfig])
        assert(cfg.id === testOnOffTypePowerPlantRow.id)
        assert(cfg.maxPower === testOnOffTypePowerPlantRow.maxPower)
        assert(cfg.minPower === testOnOffTypePowerPlantRow.minPower)
        assert(cfg.name === testOnOffTypePowerPlantRow.orgName)

      case someShit =>
        fail(s"Was expecting one of RampUpType or OnOffType, but $someShit came out!")
    }
  }

  "Conversion from PowerPlantRow to PowerPlantConfig" should "convert to UnknownType " +
    "if rampRatePower and rampRateSeconds are not specified for a RampUpType PowerPlant" in {
    val powerPlantCfg = toPowerPlantsConfig(
      Seq(
        testRampUpPowerPlantRow.copy(rampRatePower = None, rampRateSecs = None)
      )
    )

    // We expect 1 entry in the result
    assert(powerPlantCfg.powerPlantConfigSeq.length === 1)

    powerPlantCfg.powerPlantConfigSeq.head match {
      case cfg if cfg.powerPlantType == UnknownType =>
        assert(cfg.isInstanceOf[UnknownConfig])
        assert(cfg.id       === testRampUpPowerPlantRow.id)
        assert(cfg.maxPower === testRampUpPowerPlantRow.maxPower)
        assert(cfg.minPower === testRampUpPowerPlantRow.minPower)
        assert(cfg.name     === testRampUpPowerPlantRow.orgName)

      case someShit =>
        fail(s"Was expecting one of RampUpType or OnOffType, but $someShit came out!")
    }
  }
}