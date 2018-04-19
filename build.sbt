name := """parametric-image-generator"""
version       := "1.0"

scalaVersion  := "2.12.1"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies += "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.9.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "org.rogach" %% "scallop" % "2.1.3"

mainClass in assembly := Some("faces.apps.ListApplications")

assemblyJarName in assembly := "generator.jar"
