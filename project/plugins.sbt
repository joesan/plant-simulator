// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.18")

// Scala 3 Migration Plugin
addSbtPlugin("ch.epfl.scala" % "sbt-scala3-migrate" % "0.5.1")

// For removing warts, maintain code quality
addSbtPlugin("org.wartremover" %% "sbt-wartremover" % "2.4.21")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.12")

// For code coverage test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.5")

// For checkstyle
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// For formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat JAR file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.0")

// Check vulnerabilities in JAR's
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "4.3.0")

// Adds the sbt dependency tree plugin
addDependencyTreePlugin
