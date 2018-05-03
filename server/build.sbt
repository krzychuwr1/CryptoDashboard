name := "server"

version := "0.1"

scalaVersion := "2.12.6"

val Http4sVersion = "0.18.9"
val MonixVersion = "3.0.0-RC1"
val CirceVersion = "0.9.3"

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
  "io.monix" %% "monix" % MonixVersion,
  "com.github.pureconfig" %% "pureconfig" % "0.8.0",
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-core" % CirceVersion
)