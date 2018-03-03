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

import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.AppMetrics
import play.api.libs.json.Json
import com.inland24.plantsim.models._
import play.api.mvc.ControllerComponents

import scala.concurrent.Future


class ApplicationController(appCfg: AppConfig, val controllerComponents: ControllerComponents)
  extends ControllerBase {

  val host = Json.obj(
    "hostname" -> java.net.InetAddress.getLocalHost.getHostName
  )

  def metrics = Action.async {
    val allMetrics = AppMetrics.metricsAsJsValueSeq(
      AppMetrics.jvmMetrics ++ AppMetrics.dbTimerMetrics
    )
    Future.successful(
      Ok(
        Json.prettyPrint(allMetrics.foldLeft(host) {
          (acc, elem) => acc ++ elem
        })
      )
    )
  }

  def redirectDocs = Action {
    Redirect(url = "/assets/lib/swagger-ui/index.html", queryString = Map("url" -> Seq("/swagger.json")))
  }

  def appConfig = Action.async {
    Future.successful(
      Ok(Json.prettyPrint(
        Json.toJson(appCfg))
      )
    )
  }
}