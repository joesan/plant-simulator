// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.16")

// For removing warts, maintain code quality
addSbtPlugin("org.wartremover" %% "sbt-wartremover" % "2.4.20")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.11")

// For code coverage test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.5")

// For checkstyle
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// For formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat JAR file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

// Check vulnerabilities in JAR's
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "4.1.0")

// Adds the sbt dependency tree plugin
addDependencyTreePlugin
