val dottyVersion = "0.16.0-RC3"

lazy val assertion = project
  .settings(
    version := "0.1.0",
    scalaVersion := dottyVersion,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test)
