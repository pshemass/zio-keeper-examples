package zio.keeper.example

import zio.clock.sleep
import zio.console.putStrLn
import zio.duration._
import zio.keeper.ServiceDiscoveryError
import zio.keeper.discovery.Discovery
import zio.keeper.discovery.K8DnsDiscovery._
import zio.keeper.membership.SWIM._
import zio.keeper.membership.{broadcast, receive, send}
import zio.keeper.transport.Transport
import zio.keeper.transport.tcp._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.macros.delegate._
import zio.metrics.Prometheus._
import zio.metrics._
import zio.nio.InetAddress
import zio.system.env
import zio.{Chunk, ZIO, ZManaged}

object Echo extends zio.ManagedApp {

  val port = 5558

  val withSlf4j = enrichWith[Logging[String]](
    new Slf4jLogger.Live {

      override def formatMessage(msg: String): ZIO[Any, Nothing, String] =
        ZIO.succeed(msg)
    }
  )

  private def environment(port: Int) = {
    ZManaged.environment[zio.ZEnv] @@ withSlf4j >>>
    ZIO
      .require(ServiceDiscoveryError("Missing env variable ECHO_SERVICE"))(env("ECHO_SERVICE").orDie)
      .flatMap(InetAddress.byName(_).mapError(ex => ServiceDiscoveryError(ex.getMessage)))
      .flatMap(serviceAddress =>
        ZIO.environment[zio.ZEnv with Logging[String]]
          @@ withK8DnsDiscovery(serviceAddress, 30.seconds, port)
          @@ withTcpTransport(10.seconds, 10.seconds)
          )
      .toManaged_ >>>
      ZManaged.environment[zio.ZEnv with Logging[String] with Transport with Discovery] @@
        withSWIM(port) @@
        withPrometheus
  }

  def run(args: List[String]) =
    (environment(port) >>> program)
      .fold(
        _ => 1,
        _ => 0
      )


  val program = for {
    _ <- exportMetrics.toManaged_
    podName <- env("POD_NAME").orDie.get.toManaged_
    requestCount <- counter(Label("requests", Array(podName))).toManaged_
    _ <- sleep(5.seconds).toManaged_
    _ <- broadcast(Chunk.fromArray(podName.getBytes)).ignore.toManaged_
    _ <- receive
      .foreach(
        message =>
          putStrLn(new String(message.payload.toArray))
            *> send(message.payload, message.sender).ignore
            *> requestCount(1)
            *> sleep(5.seconds)
      )
      .toManaged_
  } yield ()
}
