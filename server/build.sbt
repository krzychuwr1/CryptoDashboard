name := "server"

version := "0.1"

scalaVersion := "2.12.6"

val Http4sVersion = "0.18.9"
val MonixVersion = "3.0.0-RC1"
val CirceVersion = "0.9.3"

enablePlugins(DockerPlugin)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")

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
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic-extras" % CirceVersion,
  "com.arangodb" % "arangodb-java-driver-async" % "4.3.0",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.lihaoyi" %% "sourcecode" % "0.1.4",//for extracting some additional information when integrating with java libs
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.ta4j" % "ta4j-core" % "0.11"
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