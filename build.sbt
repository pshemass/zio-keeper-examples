import Dependencies._
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion     := "2.12.9"
ThisBuild / version          := "0.1.4"
ThisBuild / organization     := "zio"
ThisBuild / organizationName := "zio"

val http4sVersion  = "0.20.0-M5"
val interopVersion = "2.0.0.0-RC2"

lazy val root = (project in file("."))
  .settings(
    name := "zio-keeper-examples",
    dockerExposedPorts := Seq(5558),
    dockerRepository := Some("rzbikson"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "dev.zio" %% "zio-keeper" % "0.0.0+59-1b358415+20190930-2230",
    libraryDependencies += "io.prometheus" % "simpleclient" % "0.6.0",
    libraryDependencies += "io.prometheus" % "simpleclient_hotspot" % "0.6.0",
    libraryDependencies +="io.prometheus" % "simpleclient_httpserver" % "0.6.0",
    libraryDependencies ++= Seq(
      //"org.http4s" %% "http4s-blaze-client" % http4sVersion,
      //"org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-argonaut"     % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
        "dev.zio"    %% "zio-interop-cats"     % interopVersion,
    ), libraryDependencies ++= Seq(
      "io.argonaut" %% "argonaut"        % "6.2.2",
      "io.argonaut" %% "argonaut-scalaz" % "6.2.2",

    )

//    libraryDependencies += "dev.zio" %% "zio-metrics" % "0.0.2"
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

scalacOptions ++= (CrossVersion.partialVersion(scalaBinaryVersion.value) match {
  case Some((2, 11)) => Seq("-Ypartial-unification", "-Ywarn-value-discard", "-target:jvm-1.8")
  case _             => Seq("-Ypartial-unification", "-Ywarn-value-discard")
})

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
