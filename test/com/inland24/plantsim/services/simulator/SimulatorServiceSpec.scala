/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inland24.plantsim.services.simulator

import org.scalatest.flatspec.AnyFlatSpec

final class SimulatorServiceSpec extends AnyFlatSpec {

  behavior of "SimulatorService"

  "SimulatorService#IntRandomValue" should "generate a random int value between a range" in {
    (1 to 10000) foreach { _ =>
      val rndm = RandomValueGeneratorService.IntRandomValue.random(200, 400)
      assert(rndm <= 400)
      assert(rndm >= 200)
    }
  }

  "SimulatorService#DoubleRandomValue" should "generate a random double value between a range" in {
    (1 to 10000) foreach { _ =>
      val rndm =
        RandomValueGeneratorService.DoubleRandomValue.random(200.0, 400.0)
      assert(rndm <= 400.0)
      assert(rndm >= 200.0)
    }
  }
}
