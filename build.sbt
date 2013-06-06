organization := "edu.washington.cs.knowitall"

name := "entity-linking"

description := "Entity linking sample implementation and experiments."

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "2.1.0",
  "edu.washington.cs.knowitall.openie" %% "openie-models" % "1.0.0-SNAPSHOT",
  "edu.washington.cs.knowitall.openie" %% "openie-linker" % "1.0.0-SNAPSHOT"
)

resolvers ++= Seq(
  "nicta" at "http://nicta.github.com/scoobi/releases",
  "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
)
