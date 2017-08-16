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

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.services.database.DBService

trait AppBindings {

  def actorSystem: ActorSystem
  def materializer: Materializer

  def dbService: DBService
  def appConfig: AppConfig
  def supervisorActor: ActorRef
}
object AppBindings {

  def apply(system: ActorSystem, actorMaterializer: Materializer): AppBindings = new AppBindings {

    override val actorSystem: ActorSystem = system
    override val materializer: Materializer = actorMaterializer

    override val appConfig: AppConfig = AppConfig.load()
    // TODO: pass this execution context in
    override val dbService = DBService(appConfig.dbConfig)(scala.concurrent.ExecutionContext.Implicits.global)

    override val supervisorActor: ActorRef =
      system.actorOf(
        SupervisorActor.props(appConfig)(monix.execution.Scheduler.Implicits.global),
        s"${appConfig.appName}-supervisor"
      )
  }
}