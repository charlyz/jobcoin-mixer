libraryDependencies ++= Seq(
  guice,
  ws,
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
  "com.typesafe.play" %% "play" % "2.6.13",
  "com.typesafe.play" %% "play-json-joda" % "2.6.9",
  "org.mockito" % "mockito-core" % "2.13.0" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "de.leanovate.play-mockws" %% "play-mockws" % "2.6.2" % Test
)

resolvers ++= Seq(
  "Typesafe Releases".at("http://repo.typesafe.com/typesafe/releases/"),
  "Typesafe Maven Releases".at("http://repo.typesafe.com/typesafe/maven-releases/"),
  "Typesafe Snapshots".at("http://repo.typesafe.com/typesafe/snapshots/"),
  "Scalaz Bintray Repo".at("http://dl.bintray.com/scalaz/releases/"),
  "Clojars".at("http://clojars.org/repo/")
)

val main = Project("gemini-jobcoin-scala", file("."))
  .enablePlugins(PlayScala)
  .settings(
    organization := "com.gemini.jobcoin",
    scalaVersion := "2.11.8",
    EclipseKeys.withJavadoc := false,
    EclipseKeys.withSource := true,
    EclipseKeys.eclipseOutput := Some("./target/eclipse-bin"),
    scalacOptions in Test ++= Seq(
      "-language:reflectiveCalls",
      "-feature"
    ),
    javaOptions in Test ++= Seq(
      "-Dlogger.resource=logback-test.xml",
      "-Denv=test",
      "-Duser.timezone=UTC",
      "-Xmx1G",
      "-Xms1G"
    ),
    // We store all the sources in src.
    scalaSource in Compile := baseDirectory.value / "src" / "app",
    scalaSource in Test := baseDirectory.value / "src" / "test",
    resourceDirectory in Test := baseDirectory.value / "src" / "test" / "resources"
  )