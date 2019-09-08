package zio.metrics


import argonaut.Argonaut.jSingleObject
import argonaut.Json
import cats.data.Kleisli
import cats.effect.ExitCode
import io.prometheus.client._
import org.http4s.{EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io.{->, /, GET, Ok}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.metrics.Metrics.HttpEnvironment
import zio.metrics.Reservoir.{Bounded, Config, ExponentiallyDecaying, Uniform}
import zio.random.Random
import zio.system.System

import scala.concurrent.duration.TimeUnit


package object metrics extends Metrics.Service[Metrics]{
  final def counter[L: Show](label: Label[L]): zio.RIO[Metrics, Long => zio.UIO[Unit]] =
    ZIO.accessM(_.metrics counter label)

  override def gauge[A, B: Semigroup, L: Show](label: Label[L])(f: Option[A] => B): RIO[Metrics, Option[A] => UIO[Unit]] =
    ZIO.accessM(_.metrics.gauge(label)(f))

  override def histogram[A: Numeric, L: Show](label: Label[L], res: Reservoir[A]): RIO[Metrics, A => UIO[Unit]] =
    ZIO.accessM(_.metrics.histogram(label, res))

  override def timer[L: Show](label: Label[L], res: Reservoir[L]): RIO[Metrics, () => UIO[Unit]] =
    ZIO.accessM(_.metrics.timer(label, res))
  override def meter[L: Show](label: Label[L]): RIO[Metrics, Double => UIO[Unit]] = ???

  override def exportMetrics: RIO[Metrics with HttpEnvironment, Unit] = ZIO.accessM(_.metrics.exportMetrics)
}

sealed trait Reservoir[+A]

object Reservoir {
  type Config = Map[String, Measurable]
  case class Uniform(config: Option[Config])               extends Reservoir[Nothing]
  case class Bounded[A](window: Long, unit: TimeUnit)      extends Reservoir[A]
  case class ExponentiallyDecaying(config: Option[Config]) extends Reservoir[Nothing]
}

case class Label[A: Show](name: A, labels: Array[String])


trait Metrics {
  protected def metricsPort: Int = 9000
  protected def defaultMetricTags: List[String] = List.empty
  def metrics: Metrics.Service[Any]

}

object Metrics {
  type HttpEnvironment = Clock with Console with System with Random with Blocking


  trait Service[R] {

    def counter[L: Show](label: Label[L]): zio.RIO[R, Long => zio.UIO[Unit]]

    def gauge[A, B: Semigroup, L: Show](label: Label[L])(f: Option[A] => B): zio.RIO[R, Option[A] => zio.UIO[Unit]]

    def histogram[A: Numeric, L: Show](
                                        label: Label[L],
                                        res: Reservoir[A] = Reservoir.ExponentiallyDecaying(None)
                                      ): zio.RIO[R, A => zio.UIO[Unit]]

    def timer[L: Show](label: Label[L], res: Reservoir[L] = Reservoir.ExponentiallyDecaying(None)): zio.RIO[R, () => UIO[Unit]]

    def meter[L: Show](label: Label[L]): zio.RIO[R, Double => zio.UIO[Unit]]

    def exportMetrics: zio.RIO[R with HttpEnvironment, Unit]
  }

  trait Prometheus extends Metrics {
    private val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val metrics = new Service[Any] {
      override def counter[L: Show](label: Label[L]): ZIO[Any, Nothing, Long => UIO[Unit]] = {
        val name = Show[L].show(label.name)
        val c = Counter
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name counter")
          .register()
        IO.effect { l: Long =>
          IO.succeedLazy(c.labels(label.labels: _*).inc(l.toDouble))
        }.orDie
      }

      override def gauge[A, B: Semigroup, L: Show](label: Label[L])(f: Option[A] => B): RIO[Any, Option[A] => UIO[Unit]] = {
        val name = Show[L].show(label.name)
        val g = Gauge
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name gauge")
          .register()
        IO.effect(
          (op: Option[A]) =>
            IO.succeedLazy(f(op) match {
              case l: Long   => g.labels(label.labels: _*).inc(l.toDouble)
              case d: Double => g.labels(label.labels: _*).inc(d)
              case _         => ()
            })
        ).orDie
      }

      override def histogram[A: Numeric, L: Show](label: Label[L], res: Reservoir[A]): RIO[Any, A => UIO[Unit]] = {
        val name = Show[L].show(label.name)
        val h = Histogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")
          .register()
        IO.effect((a: A) => IO.effect(h.labels(label.labels: _*).observe(implicitly[Numeric[A]].toDouble(a))).orDie).orDie
      }

      private def processConfig(config: Option[Config], values: (String, String, String)): (Double, Double, Int) =
        config match {
          case None => (1.0, 1.0, 1)
          case Some(m) =>
            val d1 = m.getOrElse(values._1, DoubleZ(1.0)) match {
              case DoubleZ(d) => d
              case _          => 1.0
            }

            val d2: Double = m.getOrElse(values._2, DoubleZ(1.0)) match {
              case DoubleZ(d) => d
              case _          => 1.0
            }

            val i1: Int = m.getOrElse(values._3, IntegerZ(1)) match {
              case IntegerZ(i) => i
              case _           => 1
            }
            (d1, d2, i1)
        }


      override def timer[L: Show](label: Label[L], res: Reservoir[L] = Reservoir.ExponentiallyDecaying(None)): RIO[Any, () => UIO[Unit]] = {
        val name = Show[L].show(label.name)
        val hb = Histogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")

        val builder = res match {
          case Uniform(config) =>
            val c = processConfig(config, ("start", "width", "count"))
            hb.linearBuckets(c._1, c._2, c._3)
          case ExponentiallyDecaying(config) =>
            val c = processConfig(config, ("start", "factor", "count"))
            hb.exponentialBuckets(c._1, c._2, c._3)
          case Bounded(_, _) => hb
        }

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

      override def meter[L: Show](label: Label[L]): RIO[Any, Double => UIO[Unit]] = {
        val name = Show[L].show(label.name)
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

      override def exportMetrics: RIO[HttpEnvironment, Unit] = {

        import zio.interop.catz._
        import cats.data.Kleisli
        import org.http4s.server.blaze._
        import org.http4s.{ Request, Response }
        import org.http4s.argonaut._
        import org.http4s.dsl.impl.Root
        import org.http4s.dsl.io._
        import org.http4s.implicits._

        type HttpTask[A]     = RIO[HttpEnvironment, A]

        type KleisliApp = Kleisli[HttpTask, Request[HttpTask], Response[HttpTask]]

        type HttpApp[Ctx] = Metrics => KleisliApp

        def builder[Ctx]: KleisliApp => HttpTask[Unit] =
          (app: Kleisli[HttpTask, Request[HttpTask], Response[HttpTask]]) => {
            ZIO
              .runtime[HttpEnvironment]
              .flatMap { implicit rts =>
                BlazeServerBuilder[HttpTask]
                  .bindHttp(9090)
                  .withHttpApp(app)
                  .serve
                  .compile
                  .drain
              }
          }


        def httpApp =
          (metrics: CollectorRegistry) =>
            Router(
              "/metrics" -> HttpRoutes.of[HttpTask] {
                case GET -> Root / filter =>
                  val optFilter = if (filter == "ALL") None else Some(filter)
                  val m: Json   = ReportPrinter.report(zio.metrics.PrometheusReporters.jsonPrometheusReporter)(metrics = metrics, filter = optFilter)(jSingleObject)
                  println(m)
                  RIO(Response[HttpTask](Ok).withEntity(m))
              }
            ).orNotFound

        builder(httpApp(registry)).fork.unit
      }
    }






  }
}