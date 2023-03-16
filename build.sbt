name := "zio"
version := "0.1"
scalaVersion := "3.2.2"


lazy val zioVersion = "2.0.10"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-test" % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-test-junit" % zioVersion,
  "dev.zio" %% "zio-direct" % "1.0.0-RC7"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")