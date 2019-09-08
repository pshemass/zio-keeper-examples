package zio.keeper.example

import java.util.concurrent.TimeUnit

import zio.Chunk
import zio.console.putStrLn
import zio.duration.Duration
import zio.keeper.Cluster
import zio.keeper.Cluster.Credentials
import zio.keeper.Cluster.Transport.TCPTransport
import zio.keeper.discovery.K8DnsDiscovery
import zio.metrics.Label
import zio.metrics.metrics._
import zio.nio.InetAddress

object Echo extends zio.App {

  val port = 5558

  def run(args: List[String]) =
    InetAddress.byName("echo-srv.zio-keeper.svc.cluster.local").flatMap(
      serviceDns =>
        program.provide(config(serviceDns))
    )
    .fold(_ => 1, _ => 0)

  def config(hostname: InetAddress) = new K8DnsDiscovery
    with TCPTransport
    with Credentials
    with zio.console.Console.Live
    with zio.clock.Clock.Live
    with zio.random.Random.Live
    with zio.blocking.Blocking.Live
    with zio.system.System.Live
    with zio.metrics.Metrics.Prometheus {
    override val serviceDns: InetAddress = hostname

    override val serviceDnsTimeout: Duration = Duration.apply(3, TimeUnit.SECONDS)

    override val servicePort: Int = port
  }


  val program = for {
    _ <- exportMetrics
    requestCount <- counter(Label("requests", Array("POD_NAME_HERE")))
    cluster <- Cluster.join(port)
    _ <- cluster.broadcast(Chunk.fromArray("foo".getBytes))
    _ <- cluster.receive.foreach(message =>
      putStrLn(new String(message.payload.toArray)) *>
        requestCount(1) *>
        zio.ZIO.sleep(Duration.apply(10 /*this should be random for metrics purpose*/ , TimeUnit.SECONDS)) *>
        cluster.send(message.payload, message.sender)
    )
  } yield ()
}
