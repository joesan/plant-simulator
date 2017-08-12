name := """plant-simulator"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, DockerPlugin)

scalaVersion := "2.11.11"

scalacOptions ++= Seq(
  "-language:implicitConversions",
  // turns all warnings into errors ;-)
  "-target:jvm-1.8",
  "-language:reflectiveCalls",
  "-Xfatal-warnings",
  // possibly old/deprecated linter options
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Yinline-warnings",
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

javacOptions ++= Seq(
  "-Xlint:unchecked", "-Xlint:deprecation"
)

logLevel := Level.Info

// We will use alpine os as out base image
dockerBaseImage := "anapsix/alpine-java:8_server-jre_unlimited"

// These values will be assigned the docker image name
maintainer in Docker := "https://github.com/joesan"
packageName in Docker := s"inland24/${name.value}"
version in Docker := version.value

import com.typesafe.sbt.packager.docker._
dockerCommands ++= Seq(
  Cmd("ENV", "configEnv", "default"), // This will be overridden when running!
  // This is the entry point where we can run the application against different environments
  ExecCmd("ENTRYPOINT", "sh", "-c", "bin/" + s"${executableScriptName.value}" + " -Denv=$configEnv")
)

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/public/"

doc in Compile <<= target.map(_ / "none")

libraryDependencies ++= Seq(
  ws,
  "io.monix" %% "monix" % "2.1.0",
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "org.scala-lang.modules" % "scala-async_2.11" % "0.9.6",
  "com.zaxxer" % "HikariCP" % "2.4.1",
  "com.typesafe" % "config" % "1.3.1",
  "mysql" % "mysql-connector-java" % "5.1.26",

  // test
  "com.typesafe.akka" %% "akka-testkit" % "2.5.2" % Test,
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "com.h2database" % "h2" % "1.4.186" % Test
)

