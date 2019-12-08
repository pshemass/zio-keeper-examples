package zio.metrics

import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram, Summary}
import io.prometheus.client.exporter.HTTPServer
import zio.{IO, RIO, UIO, ZIO}
import zio.metrics.Metrics.Service
import zio.macros.delegate._


object Prometheus {

  trait Live extends Metrics {
    private val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val metrics = new Service[Any] {
      override def counter(label: Label): ZIO[Any, Nothing, Long => UIO[Unit]] = {
        val name = label.name
        val c = Counter
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name counter")
          .register()
        IO.effect { l: Long =>
          IO.effectTotal(c.labels(label.labels: _*).inc(l.toDouble))
        }.orDie
      }

      override def gauge[A, B](label: Label)(f: Option[A] => B): RIO[Any, Option[A] => UIO[Unit]] = {
        val name = label.name
        val g = Gauge
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name gauge")
          .register()
        IO.effect(
          (op: Option[A]) =>
            IO.effectTotal(f(op) match {
              case l: Long   => g.labels(label.labels: _*).inc(l.toDouble)
              case d: Double => g.labels(label.labels: _*).inc(d)
              case _         => ()
            })
        ).orDie
      }

      override def histogram[A: Numeric](label: Label): RIO[Any, A => UIO[Unit]] = {
        val name = label.name
        val h = Histogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")
          .register()
        IO.effect((a: A) =>
          IO.effect(
            h.labels(label.labels: _*)
              .observe(implicitly[Numeric[A]].toDouble(a))
          ).orDie)
          .orDie
      }

      override def timer(label: Label): RIO[Any, () => UIO[Unit]] = {
        val name = label.name
        val hb = Histogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")
        val builder = hb


        val h = builder.register()

        IO.effect({
          val timer = h.startTimer()
          () =>
            IO.effect({
              timer.observeDuration()
              ()
            }).orDie
        })
      }

      override def meter(label: Label): RIO[Any, Double => UIO[Unit]] = {
        val name = label.name
        val iot = IO.succeed(
          Summary
            .build()
            .name(name)
            .labelNames(label.labels: _*)
            .help(s"$name timer")
            .register()
        )
        IO.effect((d: Double) => iot.map(s => s.labels(label.labels: _*).observe(d))).orDie
      }

      def exportMetrics: RIO[Any, Unit] = {
        ZIO.effectTotal(
          new HTTPServer(9090)
        ).unit
      }

    }
  }
  object Live extends Live

  def withPrometheus =
    enrichWith[Metrics](new Prometheus.Live {})

}
