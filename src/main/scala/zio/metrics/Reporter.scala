package zio.metrics
import argonaut.Argonaut.jEmptyObject
import argonaut.Json
import zio._

trait Reporter[M] {

  type Filter    = Option[String]

  def extractCounters: M => Filter => List[Json]
  def extractGauges: M => Filter => List[Json]
  def extractTimers: M => Filter => List[Json]
  def extractHistograms: M => Filter => List[Json]
  def extractMeters: M => Filter => List[Json]

}

object ReportPrinter {
  def report[M](R: Reporter[M])(metrics: M, filter: Option[String])(
    cons: (String, Json) => Json
  ): Json = {

    val fs = Seq(
      ("counters", R.extractCounters),
      ("gauges", R.extractGauges),
      ("timers", R.extractTimers),
      ("histograms", R.extractHistograms),
      ("meters", R.extractMeters)
    )

    fs.foldLeft(jEmptyObject)((acc0, f) => {
      val m = f._2(metrics)(filter)
      acc0.deepmerge(m.map(a => cons(f._1, a)).foldLeft(jEmptyObject)(_ deepmerge _))
    })
  }
}
