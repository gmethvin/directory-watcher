
lazy val `directory-watcher` = (project in file("."))
  .settings(
    name := "directory-watcher",
    organization := "io.methvin",
    licenses := Seq(
      "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
    ),
    crossPaths := false,
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "4.2.1",
      "com.google.guava" % "guava" % "23.0",

      "com.novocode" % "junit-interface" % "0.11" % Test,
      "io.airlift" % "command" % "0.2" % Test,
      "org.codehaus.plexus" % "plexus-utils" % "3.0.22" % Test
    )
  )

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
