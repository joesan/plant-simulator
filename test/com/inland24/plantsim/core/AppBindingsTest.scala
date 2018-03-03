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

package com.inland24.plantsim.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.inland24.plantsim.config.AppConfig
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class AppBindingsTest
    extends TestKit(ActorSystem("AppBindingsActorSystem"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  "AppBindings" must {

    val appCfg = AppConfig.load()

    "initialize application components against a default environment" in {
      val appBindings = AppBindings(system, ActorMaterializer()(system))

      appBindings.appConfig === appCfg
      appBindings.supervisorActor.path.name === s"${appCfg.appName}-supervisor"
    }
  }
}
