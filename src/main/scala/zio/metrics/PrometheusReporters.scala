package zio.metrics

import argonaut.Argonaut._
import argonaut.Json
import io.prometheus.client.{CollectorRegistry, Summary}
import java.util

import scala.collection.JavaConverters._

object PrometheusReporters {

  val metricFamily2Json: (CollectorRegistry, util.Set[String]) => List[Json] =
    (metrics: CollectorRegistry, filter: util.Set[String]) => {
      val filteredSamples = metrics.filteredMetricFamilySamples(filter)
      filteredSamples.asScala
        .map(familySamples => {
          val json = familySamples.samples.asScala
            .map(sample => jObjectAssocList(List(sample.name -> jNumberOrNull(sample.value))))
            .toList
          println(s"json: $json")
          jSingleObject(familySamples.name, jArray(json))
        })
        .toList
    }

  implicit val jsonPrometheusReporter: Reporter[CollectorRegistry] =
    new Reporter[CollectorRegistry] {
      override val extractCounters: CollectorRegistry => Filter => List[Json] =
        (metrics: CollectorRegistry) =>
          (filter: Filter) => {
            val set: util.Set[String] = new util.HashSet[String]()
            set.add(filter.getOrElse("simple_counter"))
            metricFamily2Json(metrics, set)
          }

      override val extractGauges: CollectorRegistry => Filter => List[Json] =
        (metrics: CollectorRegistry) =>
          (filter: Filter) => {
            val set: util.Set[String] = new util.HashSet[String]()
            set.add(filter.getOrElse("simple_gauge"))
            metricFamily2Json(metrics, set)
          }

      override val extractTimers: CollectorRegistry => Filter => List[Json] =
        (metrics: CollectorRegistry) =>
          (filter: Filter) => {
            val set: util.Set[String] = new util.HashSet[String]()
            set.add(filter.getOrElse("simple_timer"))
            set.add(filter.getOrElse("simple_timer_count"))
            set.add(filter.getOrElse("simple_timer_sum"))
            metricFamily2Json(metrics, set)
          }

      override val extractHistograms: CollectorRegistry => Filter => List[Json] =
        (metrics: CollectorRegistry) =>
          (filter: Filter) => {
            val set: util.Set[String] = new util.HashSet[String]()
            set.add(filter.getOrElse("simple_histogram"))
            set.add(filter.getOrElse("simple_histogram_count"))
            set.add(filter.getOrElse("simple_histogram_sum"))
            set.add(filter.getOrElse("simple_histogram_bucket"))
            metricFamily2Json(metrics, set)
          }

      override val extractMeters: CollectorRegistry => Filter => List[Json] =
        (metrics: CollectorRegistry) =>
          (filter: Filter) => {
            val set: util.Set[String] = new util.HashSet[String]()
            set.add(filter.getOrElse("simple_meter"))
            set.add(filter.getOrElse("simple_meter_count"))
            set.add(filter.getOrElse("simple_meter_sum"))
            metricFamily2Json(metrics, set)
          }
    }

}
