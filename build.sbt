name := """plant-simulator"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, DockerPlugin)

scalaVersion := "2.12.4"

scalacOptions ++= Seq(
  // Warnings propogates as errors
  "-Xfatal-warnings",
  "-language:implicitConversions",
  // turns all warnings into errors ;-)
  "-target:jvm-1.8",
  "-language:reflectiveCalls",
  "-Xfatal-warnings",
  // possibly old/deprecated linter options
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ywarn-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Xlog-free-terms",
  // enables linter options
  "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
  "-Xlint:nullary-unit", // warn when nullary methods return Unit
  "-Xlint:inaccessible", // warn about inaccessible types in method signatures
  "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
  "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
  "-Xlint:-missing-interpolator", // disables missing interpolator warning
  "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
  "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
  "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
  "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
  "-Xlint:option-implicit", // Option.apply used implicit view
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
  "-Xlint:package-object-classes", // Class or object defined in package object
  "-Xlint:unsound-match" // Pattern match may not be typesafe
)

scalacOptions in Test ++= Seq("-Yrangepos")

javacOptions ++= Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

logLevel := Level.Info

// use logback.xml when running unit tests
javaOptions in Test += "-Dlogger.file=conf/logback-test.xml"

// Docker container configurations
// We will use alpine os as out base image
dockerBaseImage := "anapsix/alpine-java:8_server-jre_unlimited"

// These values will be assigned the docker image name
maintainer in Docker := "https://github.com/joesan"
packageName in Docker := s"joesan/${name.value}"
version in Docker := version.value

import com.typesafe.sbt.packager.docker._
dockerCommands ++= Seq(
  Cmd("ENV", "configEnv", "default"), // This will be overridden when running!
  // This is the entry point where we can run the application against different environments
  ExecCmd("ENTRYPOINT",
          "sh",
          "-c",
          "bin/" + s"${executableScriptName.value}" + " -Denv=$configEnv")
)

// Scala formatter settings
scalafmtOnCompile in ThisBuild := true // all projects
scalafmtOnCompile := true // current project
scalafmtOnCompile in Compile := true // current project, specific configuration

scalafmtTestOnCompile in ThisBuild := true // all projects
scalafmtTestOnCompile := true // current project
scalafmtTestOnCompile in Compile := true // current project, specific configuration

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/public/"

doc in Compile <<= target.map(_ / "none")

libraryDependencies ++= Seq(
  ws,
  // Our streaming library
  "io.monix" %% "monix" % "2.3.3",
  "io.monix" %% "monix-cats" % "2.3.3",
  // Dependencies needed for Slick
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0",
  // For application Metrics
  "io.dropwizard.metrics" % "metrics-core" % "4.0.0",
  "io.dropwizard.metrics" % "metrics-jvm" % "4.0.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "org.scala-lang.modules" % "scala-async_2.11" % "0.9.6",
  "com.typesafe" % "config" % "1.3.1",
  // For JSON parsing
  "com.typesafe.play" %% "play-json" % "2.6.0",
  "com.typesafe.play" %% "play-json-joda" % "2.6.0",
  // JDBC driver for MySQL & H2
  "mysql" % "mysql-connector-java" % "5.1.26",
  "com.h2database" % "h2" % "1.4.186",
  // Swagger UI API Docs
  //"io.swagger" %% "swagger-play2" % "1.6.0",
  //"org.webjars" %% "webjars-play" % "2.6.0-M1",
  //"org.webjars" % "swagger-ui" % "2.2.0",
  // Test dependencies
  "com.typesafe.akka" %% "akka-testkit" % "2.5.2" % Test,
  "org.scalatest" %% "scalatest" % "3.0.9" % Test,
  "org.awaitility" % "awaitility" % "3.0.0" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test exclude ("org.slf4j", "slf4j-simple"),
  "com.github.andyglow" %% "websocket-scala-client" % "0.2.4" % Test exclude ("org.slf4j", "slf4j-simple")
)

// Assembly of the fat jar file
mainClass in assembly := Some("play.core.server.ProdServerStart")
fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

assemblyMergeStrategy in assembly := {
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need manifest files since sbt-assembly will create
    // one with the given settings
    MergeStrategy.discard
  case referenceOverrides
      if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
