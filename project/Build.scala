import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import pl.project13.scala.sbt.JmhPlugin
import com.timushev.sbt.updates.UpdatesKeys._
import com.typesafe.sbt.pgp.PgpKeys
import sbtrelease.ReleasePlugin.autoImport._
import Lib._

object Build {

  private val ghProject = "tinytest"

  private val publicationSettings =
    Lib.publicationSettings(ghProject)

  object Ver {
    val JAMM            = "0.3.1"
    val KindProjector   = "0.9.4"
    val Scala212        = "2.12.2"
  }

  def scalacFlags = Def.setting(
    Seq(
      "-deprecation",
      "-unchecked",
      "-Ywarn-dead-code",
      "-Ywarn-unused",
      "-Ywarn-value-discard",
      "-feature",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials")
    ++ (scalaVersion.value match {
      case x if x startsWith "2.12." => "-target:jvm-1.8" :: "-opt:l:method" :: Nil
    }))

  val commonSettings = ConfigureBoth(
    _.settings(
      organization                  := "com.github.japgolly.tinytest",
      homepage                      := Some(url("https://github.com/japgolly/" + ghProject)),
      licenses                      += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0")),
      scalaVersion                  := Ver.Scala212,
      scalacOptions                ++= scalacFlags.value,
      scalacOptions in Test        --= Seq("-Ywarn-dead-code"),
      shellPrompt in ThisBuild      := ((s: State) => Project.extract(s).currentRef.project + "> "),
      triggeredMessage              := Watched.clearWhenTriggered,
      incOptions                    := incOptions.value.withNameHashing(true),
      updateOptions                 := updateOptions.value.withCachedResolution(true),
      dependencyUpdatesExclusions   := moduleFilter(organization = "org.scala-lang") |
                                       moduleFilter(organization = "org.eclipse.jetty"),
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseTagComment             := s"v${(version in ThisBuild).value}",
      releaseVcsSign                := true,
      addCompilerPlugin("org.spire-math" %% "kind-projector" % Ver.KindProjector))
    .configure(
      addCommandAliases(
        "/"   -> "project root",
        "L"   -> "root/publishLocal",
        "C"   -> "root/clean",
        "T"   -> ";root/clean;root/test",
        "TL"  -> ";T;L",
        "c"   -> "compile",
        "tc"  -> "test:compile",
        "t"   -> "test",
        "to"  -> "test-only",
        "tq"  -> "test-quick",
        "cc"  -> ";clean;compile",
        "ctc" -> ";clean;test:compile",
        "ct"  -> ";clean;test")))

  // ===================================================================================================================

  lazy val root =
    Project("root", file("."))
      .settings(name := "TinyTest")
      .configure(commonSettings.jvm, preventPublication)
      .aggregate(coreJVM, coreJS)

  lazy val coreJVM = core.jvm
  lazy val coreJS  = core.js
  lazy val core = crossProject
    .configureCross(commonSettings, publicationSettings)

  /*
  lazy val bench = project.in(file("bench"))
    .dependsOn(recursionJVM % "compile->test")
    .enablePlugins(JmhPlugin)
    .configure(commonSettings.jvm, preventPublication)
    .settings(
      name := "bench",
      libraryDependencies += "com.github.jbellis" % "jamm" % Ver.JAMM,
      fork := true,
      javaOptions ++= Seq("-server", "-Xss8M"),

      // Add the JAMM jar as an agent
      javaOptions in run := {
        val classPath = (dependencyClasspath in Compile).value
        val jammJar = classPath.collectFirst {
          case sbt.Attributed(f) if f.getName.matches("jamm-[0-9.]+\\.jar") => f.getAbsolutePath
        }.get
        val oldOptions = (javaOptions in run).value
        val newOptions = oldOptions :+ s"-javaagent:$jammJar"
        newOptions
      }
    )
    */

}
