name := "rm-play-mongo"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka22"
)



play.Project.playScalaSettings
