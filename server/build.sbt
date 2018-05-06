name := "server"

version := "0.1"

scalaVersion := "2.12.6"

val Http4sVersion = "0.18.9"
val MonixVersion = "3.0.0-RC1"
val CirceVersion = "0.9.3"

enablePlugins(DockerPlugin)

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Xlint",
  "-Ypartial-unification",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.http4s" %% "rho-swagger" % "0.18.0",
  "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,
  "io.monix" %% "monix" % MonixVersion,
  "com.github.pureconfig" %% "pureconfig" % "0.8.0",
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-core" % CirceVersion
)

mainClass in assembly := Some("pl.edu.agh.crypto.dashboard.Launcher")
dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"
  new Dockerfile {
    from("java")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-Duser.timezone=UTC", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(
  ImageName(
    repository = "dashboard_server",
    tag = Some(version.value)
  ),
  ImageName(
    repository = "dashboard_server",
    tag = Some("latest")
  )
)