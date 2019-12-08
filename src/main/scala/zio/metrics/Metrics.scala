package zio.metrics


import zio._

sealed trait Reservoir

object Reservoir {
  case object Uniform              extends Reservoir
  case object ExponentiallyDecaying extends Reservoir
}

case class Label(name: String, labels: Array[String])


trait Metrics {
  def metrics: Metrics.Service[Any]

}

object Metrics {

  trait Service[R] {

    def counter(label: Label): zio.RIO[R, Long => zio.UIO[Unit]]

    def gauge[A, B](label: Label)(f: Option[A] => B): zio.RIO[R, Option[A] => zio.UIO[Unit]]

    def histogram[A: Numeric](label: Label): zio.RIO[R, A => zio.UIO[Unit]]

    def timer(label: Label): zio.RIO[R, () => UIO[Unit]]

    def meter(label: Label): zio.RIO[R, Double => zio.UIO[Unit]]

    def exportMetrics: zio.RIO[R, Unit]

  }


}