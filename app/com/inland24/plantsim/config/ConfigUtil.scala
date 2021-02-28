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

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}

import java.util.Locale

object ConfigUtil {

  sealed trait ConfigSource
  object ConfigSource {
    final case class FromFile(path: String) extends ConfigSource
    final case class FromResource(name: String) extends ConfigSource
  }

  def getConfigSource: ConfigSource =
    Option(System.getProperty("config.file")) match {
      case Some(path) if new File(path).exists() =>
        ConfigSource.FromFile(path)

      case _ =>
        val opt1 = Option(System.getProperty("ENV", "")).filter(_.nonEmpty)
        val opt2 = Option(System.getProperty("env", "")).filter(_.nonEmpty)

        opt1.orElse(opt2) match {
          case Some(envName) =>
            val name =
              s"application.${envName.toLowerCase(Locale.ENGLISH)}.conf"
            ConfigSource.FromResource(name)
          case None =>
            ConfigSource.FromResource("application.conf")
        }
    }

  def loadFromEnv(): Config = {
    getConfigSource match {
      case ref @ ConfigSource.FromFile(path) =>
        ConfigFactory.parseFile(new File(path)).resolve()

      case ref @ ConfigSource.FromResource(name) =>
        ConfigFactory.load(name).resolve()
    }
  }
}
