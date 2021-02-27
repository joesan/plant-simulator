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

import java.lang.management.ManagementFactory
import com.codahale.metrics.{MetricRegistry, MetricSet, Timer}
import com.codahale.metrics.jvm._
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._

object AppMetrics {

  // the registry
  val registry: MetricRegistry = new MetricRegistry()

  // the registration
  registerMetrics("gc", new GarbageCollectorMetricSet(), registry)
  registerMetrics(
    "buffers",
    new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer),
    registry)
  registerMetrics("memory", new MemoryUsageGaugeSet(), registry)
  registerMetrics("threads", new ThreadStatesGaugeSet(), registry)

  // register timers
  val timer: Timer = registry.timer("power-plant-repo-as-task")

  private def registerMetrics(metricName: String,
                              metricSet: MetricSet,
                              registry: MetricRegistry): Unit = {
    for ((key, value) <- metricSet.getMetrics.asScala)
      registry.register(s"$metricName.$key", value)
  }

  final case class MetricGroup(metricGroupName: String, metrics: Seq[Metric])
  final case class Metric(metricName: String, metricValue: String)

  def metricsAsJsValueSeq(metricGroup: Seq[MetricGroup]): Seq[JsObject] =
    metricGroup.map { metricGroup =>
      Json.obj(
        s"${metricGroup.metricGroupName}" -> metricGroup.metrics.map({ metric =>
          Json.obj(metric.metricName -> metric.metricValue)
        })
      )
    }

  def dbTimerMetrics: Seq[MetricGroup] = {
    registry.getTimers.asScala.toSeq.map {
      case (key, timerVal) =>
        MetricGroup(
          key,
          Seq(
            Metric("fifteenMinuteRate", timerVal.getFifteenMinuteRate.toString),
            Metric("fiveMinuteRate", timerVal.getFiveMinuteRate.toString),
            Metric("oneMinuteRate", timerVal.getOneMinuteRate.toString),
            Metric("meanRate", timerVal.getMeanRate.toString),
            Metric("count", timerVal.getCount.toString)
          )
        )
    }
  }

  def jvmMetrics: Seq[MetricGroup] = {
    val splitFn: String => Seq[String] =
      str => str.split("[.]").toSeq

    registry.getGauges.asScala.toSeq
      .groupBy {
        case (key, _) => s"${splitFn(key).head}"
      }
      .map {
        case (metricGroupKey, metricGauge) =>
          val metrics = metricGauge.map {
            case (metricName, gauge) =>
              Metric(
                splitFn(metricName).tail
                  .mkString("_")
                  .toLowerCase
                  .replace("-", "_"),
                gauge.getValue.toString
              )
          }
          MetricGroup(metricGroupKey, metrics)
      }
      .toSeq
  }
}
