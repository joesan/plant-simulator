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

package com.inland24.plantsim.controllers

import java.io.File

import com.inland24.plantsim.core.Bootstrap
import org.scalatestplus.play.FakeApplicationFactory
import play.api._
import play.api.inject._
import play.core.DefaultWebCommands

/**
  * Test utility to get a FakeApplication, used when unit testing
  * Controllers
  */
trait ApplicationTestFactory extends FakeApplicationFactory {

  override def fakeApplication(): Application = {
    val env = Environment.simple(new File("."))
    val configuration = Configuration.load(env)
    val context = ApplicationLoader.Context.create(
      environment = env,
      initialSettings = Map.empty,
      lifecycle = new DefaultApplicationLifecycle()
    )
    new Bootstrap().load(context)
  }
}
