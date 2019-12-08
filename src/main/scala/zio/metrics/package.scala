package zio


package object metrics extends Metrics.Service[Metrics]{
  final def counter(label: Label): zio.RIO[Metrics, Long => zio.UIO[Unit]] =
    ZIO.accessM(_.metrics counter label)

  override def gauge[A, B](label: Label)(f: Option[A] => B): RIO[Metrics, Option[A] => UIO[Unit]] =
    ZIO.accessM(_.metrics.gauge(label)(f))

  override def histogram[A: Numeric](label: Label): RIO[Metrics, A => UIO[Unit]] =
    ZIO.accessM(_.metrics.histogram(label))

  override def timer(label: Label): RIO[Metrics, () => UIO[Unit]] =
    ZIO.accessM(_.metrics.timer(label))

  override def meter(label: Label): RIO[Metrics, Double => UIO[Unit]] =
    ZIO.accessM[Metrics](_.metrics.meter(label))

  override def exportMetrics: RIO[Metrics, Unit] =
    ZIO.accessM(_.metrics.exportMetrics)
}