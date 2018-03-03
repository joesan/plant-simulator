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

import com.inland24.plantsim.controllers.{ApplicationController, PowerPlantController, PowerPlantOperationsController}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import play.api.{Application, BuiltInComponentsFromContext, Configuration, _}
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
    extends BuiltInComponentsFromContext(context)
      with StrictLogging with _root_.controllers.AssetsComponents {

    // We use the Monix Scheduler
    implicit val s = monix.execution.Scheduler.Implicits.global

    def stop(bindings: AppBindings) = {
      logger.info("Stopping application :: plant-simulator")
      bindings.globalChannel.onComplete()
    }

    def start = {
      logger.info("Starting application :: plant-simulator")
      AppBindings(actorSystem, materializer)
    }

    // 0. Set the filters
    lazy val loggingFilter: LoggingFilter = new LoggingFilter()
    override lazy val httpFilters = Seq(loggingFilter)

    //override val configuration = context.initialConfiguration

    // 1. create the dependencies that will be injected
    lazy val appBindings = start

    // 2. inject the dependencies into the controllers
    // TODO: The dependecies below are for Swagger UI, which is not working at the moment!!!!
    //lazy val apiHelpController = new ApiHelpController(DefaultControllerComponents)
    //lazy val webJarAssets = new WebJarAssets(httpErrorHandler, configuration, environment)
    lazy val applicationController = new ApplicationController(appBindings.appConfig, controllerComponents)
    lazy val powerPlantController = new PowerPlantController(appBindings, controllerComponents)
    lazy val powerPlantOpsController = new PowerPlantOperationsController(appBindings, controllerComponents)
    //lazy val assets = new Assets(httpErrorHandler)
    override def router: Router = new Routes(
      httpErrorHandler,
      assets,
      applicationController,
      powerPlantController,
      powerPlantOpsController
      //apiHelpController,
      //webJarAssets
    )

    // 3. add the shutdown hook to properly dispose all connections
    applicationLifecycle.addStopHook { () => Future(stop(appBindings)) }

    //override def config(): Config = configuration.underlying
  }

  override def load(context: Context): Application = {
    val configuration = Configuration(ConfigFactory.load())

    val newContext = context.copy(initialConfiguration = configuration)
    LoggerConfigurator(newContext.environment.classLoader)
      .foreach(_.configure(newContext.environment))

    new App(newContext).application
  }
}