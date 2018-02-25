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

import com.codahale.metrics.{MetricRegistry, MetricSet}
import com.codahale.metrics.jvm._
import scala.collection.JavaConverters._


object AppMetrics {

  // the registry
  val registry = new MetricRegistry()

  // the registration
  registerMetrics("gc",      new GarbageCollectorMetricSet(), registry)
  registerMetrics("buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer), registry)
  registerMetrics("memory",  new MemoryUsageGaugeSet(), registry)
  registerMetrics("threads", new ThreadStatesGaugeSet(), registry)

  private def registerMetrics(metricName: String, metricSet: MetricSet, registry: MetricRegistry) = {
    for ((key, value) <- metricSet.getMetrics.asScala)
      registry.register(s"$metricName.$key", value)
  }

  case class MetricGroup(metricGroupName: String, metrics: Seq[Metric])
  case class Metric(metricName: String, metricValue: String)

  // meter to measure the rate of OpenTSDB writes!
  val openTSDBWriteMeter = registry.meter("openTSDB.write")

  def jvmMetrics: Seq[MetricGroup] = {

    val splitFn: String => Seq[String] =
      str => str.split("[.]").toSeq

    registry.getGauges.asScala.toSeq.groupBy {
      case (key, _) => s"${splitFn(key).head}"
    }.map {
      case (metricGroupKey, metricGauge) =>
        val metrics = metricGauge.map {
          case (metricName, gauge) =>
            Metric(
              splitFn(metricName).tail.mkString("_").toLowerCase.replace("-", "_"),
              gauge.getValue.toString
            )
        }
        MetricGroup(metricGroupKey, metrics)
    }.toSeq
  }
}