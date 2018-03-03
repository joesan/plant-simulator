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

import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class ConfigUtilTest extends FlatSpec with BeforeAndAfterAll {

  private def clearAll() = {
    System.clearProperty("config.file")
    System.clearProperty("env")
    System.clearProperty("ENV")
  }

  override def beforeAll() = clearAll()

  override def afterAll() = clearAll()

  "loadFromEnv" should "load the default config when nothing is specified from the environment" in {
    val config = ConfigUtil.loadFromEnv()
    assert(config.getString("environment") === "default")
  }

  "loadFromEnv" should "load the test config when set as a system property" in {
    System.setProperty("config.file", "conf/application.test.conf")
    val config = ConfigUtil.loadFromEnv()
    assert(config.getString("environment") === "test")
    assert(config.getString("db.driver") === "org.h2.Driver")
  }

  "loadFromEnv" should "load the test config when set as a environment system property" in {
    System.setProperty("env", "test")
    val config = ConfigUtil.loadFromEnv()
    assert(config.getString("environment") === "test")
    assert(config.getString("db.driver") === "org.h2.Driver")
  }
}
