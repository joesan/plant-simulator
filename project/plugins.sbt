// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.10")

// For removing warts, maintain code quality
addSbtPlugin("org.wartremover" %% "sbt-wartremover" % "2.4.16")

// The sbt native packager - needed for docker builds
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.7")

// For code coverage test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.2")

// For checkstyle
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// For formatting Scala source code
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")

// Build fat JAR file
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0")

// Check vulnerabilities in JAR's
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "3.3.0")
