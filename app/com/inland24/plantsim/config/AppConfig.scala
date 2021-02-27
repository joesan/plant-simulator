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

package com.inland24.plantsim.config

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.duration._
import scala.util.Try

/**
  * Type-safe configuration used throughout the application.
  */
final case class AppConfig(
    environment: String,
    appName: String,
    dbConfig: DBConfig
)
final case class DBConfig(url: String,
                          user: Option[String],
                          password: Option[String],
                          driver: String,
                          recordCountPerPage: Int,
                          enableSubscription: Boolean,
                          refreshInterval: FiniteDuration) {

  lazy val slickDriver: JdbcProfile = driver match {
    case "com.mysql.jdbc.Driver" =>
      Class.forName(driver)
      slick.jdbc.MySQLProfile
    case "org.h2.Driver" =>
      Class.forName(driver)
      slick.jdbc.H2Profile
  }

  lazy val database: JdbcBackend.DatabaseDef = {
    Database.forURL(url,
                    user.getOrElse(""),
                    password.getOrElse(""),
                    driver = driver)
  }
}
object AppConfig {
  def load(): AppConfig =
    load(ConfigUtil.loadFromEnv())

  def load(config: Config): AppConfig = {
    AppConfig(
      environment = config.getString("environment"),
      appName = config.getString("appName"),
      dbConfig = DBConfig(
        url = config.getString("db.url"),
        user =
          Try(config.getString("db.username")).toOption.filterNot(_.isEmpty),
        password =
          Try(config.getString("db.password")).toOption.filterNot(_.isEmpty),
        driver = config.getString("db.driver"),
        enableSubscription =
          Try(config.getBoolean("db.enableSubscription")).toOption
            .getOrElse(true),
        recordCountPerPage = config.getInt("db.recordsPerPage"),
        refreshInterval =
          config.getDuration("db.refreshInterval", TimeUnit.MILLISECONDS).millis
      )
    )
  }
}
