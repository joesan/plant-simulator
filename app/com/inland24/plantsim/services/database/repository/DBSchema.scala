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

import java.sql.Timestamp
import com.inland24.plantsim.models.PowerPlantType
import com.inland24.plantsim.services.database.models.PowerPlantRow
import org.joda.time.{DateTime, DateTimeZone}
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.{CanBeQueryCondition, ProvenShape}
import slick.sql.FixedSqlAction

import scala.language.higherKinds

class DBSchema private (val driver: JdbcProfile) {

  import driver.api._

  /**
    * Mapping for using Joda Time and SQL Time.
    */
  implicit def dateTimeMapping
    : JdbcType[DateTime] with BaseTypedType[DateTime] =
    MappedColumnType.base[DateTime, java.sql.Timestamp](
      dt => new Timestamp(dt.getMillis),
      ts => new DateTime(ts.getTime, DateTimeZone.UTC)
    )

  /**
    * Mapping for using PowerPlantType conversions.
    */
  implicit def powerPlantTypeMapping
    : JdbcType[PowerPlantType] with BaseTypedType[PowerPlantType] =
    MappedColumnType.base[PowerPlantType, String](
      powerPlantType => PowerPlantType.asString(powerPlantType),
      powerPlantTypeStr => PowerPlantType.fromString(powerPlantTypeStr)
    )

  case class MaybeFilter[X, Y, C[_]](query: slick.lifted.Query[X, Y, C]) {
    def filter[T, R: CanBeQueryCondition](data: Option[T])(
        f: T => X => R): MaybeFilter[X, Y, C] = {
      data.map(v => MaybeFilter(query.withFilter(f(v)))).getOrElse(this)
    }
  }

  ///////////////// PowerPlant Table
  /**
    * The PowerPlant details are maintained in the PowerPlant table
    */
  class PowerPlantTable(tag: Tag)
      extends Table[PowerPlantRow](tag, "powerPlant") {
    def id: Rep[Option[Int]] = column[Option[Int]]("powerPlantId", O.PrimaryKey)
    def orgName: Rep[String] = column[String]("orgName")
    def isActive: Rep[Boolean] = column[Boolean]("isActive")
    def minPower: Rep[Double] = column[Double]("minPower")
    def maxPower: Rep[Double] = column[Double]("maxPower")
    def powerRampRate: Rep[Option[Double]] = column[Option[Double]]("rampRate")
    def rampRateSecs: Rep[Option[Long]] = column[Option[Long]]("rampRateSecs")
    def powerPlantType: Rep[PowerPlantType] =
      column[PowerPlantType]("powerPlantType")
    def createdAt: Rep[DateTime] = column[DateTime]("createdAt")
    def updatedAt: Rep[DateTime] = column[DateTime]("updatedAt")

    def * : ProvenShape[PowerPlantRow] = {
      (id,
       orgName,
       isActive,
       minPower,
       maxPower,
       powerRampRate,
       rampRateSecs,
       powerPlantType,
       createdAt,
       updatedAt) <>
        (PowerPlantRow.tupled, PowerPlantRow.unapply)
    }
  }

  // Contains the SQL query for [[ PowerPlantTable ]]
  object PowerPlantTable {

    /**
      * A TableQuery can be used for composing queries, inserts
      * and pretty much anything related to the sensors table.
      */
    val all: TableQuery[PowerPlantTable] = TableQuery[PowerPlantTable]

    /**
      * Fetch all active PowerPlant's
      */
    def activePowerPlants: Query[PowerPlantTable, PowerPlantRow, Seq] = {
      all.filter(_.isActive === true)
    }

    /**
      * Deactivate a PowerPlant
      * (i.e., to set the isActive flag to false)
      */
    def deActivatePowerPlant(
        id: Int): FixedSqlAction[Int, NoStream, Effect.Write] = {
      all
        .filter(_.id === id)
        .map(elem => elem.isActive)
        .update(false)
    }

    /**
      * Filter and fetch PowerPlant by organization id
      */
    def powerPlantById(id: Int): Query[PowerPlantTable, PowerPlantRow, Seq] = {
      all.filter(_.id === id)
    }

    def powerPlantsFor(criteriaPowerPlantType: Option[PowerPlantType],
                       criteriaOrgName: Option[String],
                       criteriaOnlyActive: Option[Boolean])
      : Query[PowerPlantTable, PowerPlantRow, Seq] = {
      for {
        filtered <- all.filter(
          f =>
            criteriaOrgName
              .map(a => f.orgName like s"%$a%")
              .getOrElse(slick.lifted.LiteralColumn(true)) &&
              criteriaOnlyActive
                .map(b => f.isActive === b)
                .getOrElse(slick.lifted.LiteralColumn(true)) &&
              criteriaPowerPlantType
                .map(d => f.powerPlantType === d)
                .getOrElse(slick.lifted.LiteralColumn(true)))
      } yield filtered
    }
  }
}
object DBSchema {
  def apply(driver: JdbcProfile): DBSchema = {
    new DBSchema(driver)
  }
}
