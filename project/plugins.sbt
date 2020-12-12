// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.6")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")

// For code coverage test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

// For checkstyle
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// For formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat jar file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")