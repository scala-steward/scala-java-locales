resolvers += Resolver.sonatypeRepo("public")

val scalaJSVersion =
  Option(System.getenv("SCALAJS_VERSION")).getOrElse("1.0.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
addSbtPlugin("io.github.cquiroz" % "sbt-locales" % "0.3.2")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.2")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.11")

libraryDependencies ++= {
  if (scalaJSVersion.startsWith("1.0"))
    Seq("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0")
  else Seq.empty
}
