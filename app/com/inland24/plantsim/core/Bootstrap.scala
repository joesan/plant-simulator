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

import com.inland24.plantsim.controllers.PowerPlantOperationsController
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import controllers.Assets
import play.api.{Application, BuiltInComponentsFromContext, Configuration, _}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.ApplicationLoader.Context

// these two imports below are needed for the routes resolution
import play.api.routing.Router
import router.Routes

import scala.concurrent.Future


/**
  * Bootstrap the application by performing a compile time DI
  */
final class Bootstrap extends ApplicationLoader with LazyLogging {

  private[this] class App(context: Context)
    extends BuiltInComponentsFromContext(context) with AhcWSComponents
      with StrictLogging {

    // We use the Monix Scheduler
    implicit val s = monix.execution.Scheduler.Implicits.global

    def stop(bindings: AppBindings) = {
      logger.info("Stopping application :: plant-simulator")
      //bindings.globalChannel.publishChannel.onComplete()
    }

    def start = {
      logger.info("Starting application :: plant-simulator")
      AppBindings(actorSystem, materializer)
    }

    // 0. Set the filters
    lazy val loggingFilter: LoggingFilter = new LoggingFilter()
    override lazy val httpFilters = Seq(loggingFilter)

    // 1. create the dependencies that will be injected
    lazy val appBindings = start

    // 2. inject the dependencies into the controllers
    lazy val applicationController = new PowerPlantOperationsController(appBindings)
    lazy val assets = new Assets(httpErrorHandler)
    override def router: Router = new Routes(
      httpErrorHandler, applicationController, assets
    )

    // 3. add the shutdown hook to properly dispose all connections
    applicationLifecycle.addStopHook { () => Future(stop(appBindings)) }
  }

  override def load(context: Context): Application = {
    val configuration = Configuration(ConfigFactory.load())

    val newContext = context.copy(initialConfiguration = configuration)
    LoggerConfigurator(newContext.environment.classLoader)
      .foreach(_.configure(newContext.environment))

    new App(newContext).application
  }
}