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

package com.inland24.plantsim.services.simulator

import scala.util.Random


trait RandomValueGeneratorService[T] {

  def random(from: T, within: T): T
}
object RandomValueGeneratorService {

  object IntRandomValue extends RandomValueGeneratorService[Int] {
    val random = new Random(81)
    override def random(from: Int, within: Int): Int =
      from + random.nextInt((within - from) + 1)
  }

  object DoubleRandomValue extends RandomValueGeneratorService[Double] {
    val random = new Random(92)
    override def random(from: Double, within: Double): Double =
      from + (random.nextDouble() * (within - from) )
  }

  /* TODO: Check if this method is needed, if not remove it!
  object StringRandomValue extends RandomValueGeneratorService[String] {
    val rndm = new Random(9988)
    override def random(from: String, within: String): String = // TODO: make use of from and within
      rndm.nextString(from.length + within.length)
  } */
}