organization in ThisBuild := "io.methvin"
licenses in ThisBuild := Seq(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
)
homepage in ThisBuild := Some(url("https://github.com/gmethvin/directory-watcher"))
scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/gmethvin/directory-watcher"),
    "scm:git@github.com:gmethvin/directory-watcher.git"
  )
)
developers in ThisBuild := List(
  Developer("gmethvin", "Greg Methvin", "greg@methvin.net", new URL("https://github.com/gmethvin"))
)

scalafmtOnCompile in ThisBuild := true

publishMavenStyle in ThisBuild := true
publishTo in ThisBuild := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

def commonSettings = Seq(
  fork in Test := true,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  scalacOptions += "-target:jvm-1.8",
  javaOptions in Test ++= Seq("-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"),
  libraryDependencies ++= Seq(
    "com.novocode" % "junit-interface" % "0.11" % Test,
    "org.slf4j" % "slf4j-simple" % "1.7.26" % Test
  )
)

// directory-watcher is a Java-only library. No Scala dependencies should be added.
lazy val `directory-watcher` = (project in file("core"))
  .settings(commonSettings)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.3.1",
      "org.slf4j" % "slf4j-api" % "1.7.26",
      "io.airlift" % "command" % "0.3" % Test,
      "com.google.guava" % "guava" % "27.0-jre" % Test,
      "org.codehaus.plexus" % "plexus-utils" % "3.2.0" % Test,
      "commons-io" % "commons-io" % "2.6" % Test,
      "org.awaitility" % "awaitility" % "3.1.6" % Test
    )
  )

// directory-watcher-better-files is a Scala library.
lazy val `directory-watcher-better-files` = (project in file("better-files"))
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq(scalaVersion.value, "2.13.0-RC1"),
    crossPaths := true,
    libraryDependencies ++= Seq(
      "com.github.pathikrit" %% "better-files" % "3.8.0",
      "org.scalatest" %% "scalatest" % "3.0.8-RC2" % Test
    )
  )
  .dependsOn(`directory-watcher`)

lazy val root = (project in file("."))
  .settings(
    PgpKeys.publishSigned := {},
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    skip in publish := true
  )
  .aggregate(`directory-watcher`, `directory-watcher-better-files`)

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
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
