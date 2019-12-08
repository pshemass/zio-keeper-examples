import Dependencies._
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion     := "2.12.9"
ThisBuild / version          := "0.1.6"
ThisBuild / organization     := "zio"
ThisBuild / organizationName := "zio"


lazy val root = (project in file("."))
  .settings(
    name := "zio-keeper-examples",
    dockerExposedPorts := Seq(5558, 9090),
    dockerRepository := Some("rzbikson"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "dev.zio" %% "zio-keeper" % "0.0.0+94-26973556",
    libraryDependencies += "io.prometheus" % "simpleclient" % "0.6.0",
    libraryDependencies += "io.prometheus" % "simpleclient_hotspot" % "0.6.0",
    libraryDependencies +="io.prometheus" % "simpleclient_httpserver" % "0.6.0",
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

scalacOptions ++= (CrossVersion.partialVersion(scalaBinaryVersion.value) match {
  case Some((2, 11)) => Seq("-Ypartial-unification", "-Ywarn-value-discard", "-target:jvm-1.8")
  case _             => Seq("-Ypartial-unification", "-Ywarn-value-discard")
})

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
