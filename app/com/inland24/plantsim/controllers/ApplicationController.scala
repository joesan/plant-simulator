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

import com.codahale.metrics.{Meter, Timer}
import com.inland24.plantsim.config.AppConfig
import com.inland24.plantsim.core.AppMetrics
import play.api.libs.json.{JsObject, Json}
import com.inland24.plantsim.models._
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.collection.JavaConverters._


class ApplicationController(appCfg: AppConfig)
  extends Controller {

  val json = Json.obj(
    "hostname" -> java.net.InetAddress.getLocalHost.getHostName
  )
  
  private def timers(json: JsObject, seq: Seq[(String, Timer)]) = {
    seq.foldLeft(json) { (acc, elem) =>
      val (key, value) = elem
      Json.obj(
        "key"  -> key,
        "type" -> "meter",
        "count" -> value.getCount,
        "rate_15_mins" -> value.getFifteenMinuteRate,
        "rate_5_mins" -> value.getFiveMinuteRate,
        "rate_1_min" -> value.getOneMinuteRate,
        "mean" -> value.getMeanRate
      )
    }
  }

  def metrics = Action.async {
    Future.successful(
      Ok(
        timers(json, AppMetrics.registry.getTimers.asScala.toSeq)
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