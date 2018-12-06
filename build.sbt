name := "node_scala"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.14",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.14" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.parboiled" %% "parboiled" % "2.1.4",
  "commons-daemon" % "commons-daemon" % "1.0.15",
  "com.typesafe.akka" %% "akka-http" % "10.1.4",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.4" % Test,
  "com.typesafe.akka" %% "akka-stream" % "2.5.15",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.15" % Test,
  "org.parboiled" %% "parboiled" % "2.1.4"
)
//val additionalClasses = file("/usr/share/node")
//unmanagedClasspath in Compile += additionalClasses
//unmanagedClasspath in Runtime += additionalClasses
assemblyJarName in assembly := "node-0.0.9.jar"
test in assembly := {}
mainClass in assembly := Some("ru.serbis.okto.node.Main")
target in javah := file("jni")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
