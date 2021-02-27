// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.7")

// For removing warts, maintain code quality
addSbtPlugin("org.wartremover" %% "sbt-wartremover" % "2.4.13")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.0")

// For code coverage test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

// For checkstyle
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// For formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat jar file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
