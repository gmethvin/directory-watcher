inThisBuild(
  Seq(
    organization := "io.methvin",
    scalaVersion := "2.13.10",
    crossScalaVersions := Seq("2.12.15", "2.13.10", "3.1.1"),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-unchecked",
      "-feature",
      "-Xlint"
    ),
    // Central Portal publishing settings
    publishMavenStyle := true,
    Test / publishArtifact := false,
    pomIncludeRepository := { _ => false },
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    homepage := Some(url("https://github.com/gmethvin/directory-watcher")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/gmethvin/directory-watcher"),
        "scm:git@github.com:gmethvin/directory-watcher.git"
      )
    ),
    developers := List(
      Developer(
        id = "gmethvin",
        name = "Greg Methvin",
        email = "greg@methvin.net",
        url = url("https://github.com/gmethvin")
      )
    )
  )
)

// Set publishTo for Central Portal
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

def commonSettings =
  Seq(
    publishMavenStyle := true,
    Test / fork := true,
    compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    scalacOptions ++= Seq("-release", "8"),
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.2" % Test,
      "ch.qos.logback" % "logback-classic" % "1.3.12" % Test
    )
  )

// directory-watcher is a Java-only library. No Scala dependencies should be added.
lazy val `directory-watcher` = (project in file("core"))
  .settings(commonSettings)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.16.0",
      "org.slf4j" % "slf4j-api" % "1.7.36",
      "io.airlift" % "command" % "0.3" % Test,
      "com.google.guava" % "guava" % "33.3.0-jre" % Test,
      "org.codehaus.plexus" % "plexus-utils" % "3.5.1" % Test,
      "commons-io" % "commons-io" % "2.17.0" % Test,
      "org.awaitility" % "awaitility" % "4.2.2" % Test
    )
  )

// directory-watcher-better-files is a Scala library.
lazy val `directory-watcher-better-files` = (project in file("better-files"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Seq(scalaVersion.value, "2.13.14", "2.12.20"),
    crossPaths := true,
    libraryDependencies ++= Seq(
      "com.github.pathikrit" %% "better-files" % "3.9.2",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
  .dependsOn(`directory-watcher`)

lazy val root = (project in file("."))
  .settings(
    PgpKeys.publishSigned := {},
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    publish / skip := true
  )
  .aggregate(`directory-watcher`, `directory-watcher-better-files`)

// sbt-release configuration for Central Portal
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseCrossBuild := true
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"), // This creates the artifacts locally
  releaseStepCommand("sonaUpload"), // Upload to Central Portal
  releaseStepCommand("sonaRelease"), // Release on Central Portal
  setNextVersion,
  commitNextVersion,
  pushChanges
)
