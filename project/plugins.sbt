// The Play plugin
//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.9")
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.11")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

// For Formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat jar file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")