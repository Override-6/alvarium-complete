import mill._
import mill.scalalib._

trait AlvariumModule extends ScalaModule {
  def scalaVersion = "3.4.0"

  override def unmanagedClasspath = T {
    Agg.from(
      os.list(millSourcePath / os.up / "lib").map(PathRef(_))
    )
  }

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.9.3",
    ivy"org.apache.logging.log4j:log4j-core:2.21.0",
    ivy"com.google.code.findbugs:jsr305:2.0.2"
  ) ++ otherDeps

  def otherDeps = Agg[Dep]()
}

object master extends AlvariumModule {
  override def moduleDeps = `alvarium-node` :: Nil
  override def otherDeps = Agg(
    ivy"dev.zio::zio:2.1-RC1",
    ivy"dev.zio::zio-http:3.0.0-RC6",
    ivy"dev.zio::zio-json:0.6.2",
    ivy"org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5",
    ivy"io.getquill::quill-jdbc-zio:4.8.3",
    ivy"org.postgresql:postgresql:42.3.1"
  )
}
object worker extends AlvariumModule {
  override def moduleDeps = `alvarium-node` :: Nil

  override def otherDeps = Agg(
    ivy"dev.zio::zio:2.1-RC1",
    ivy"dev.zio::zio-http:3.0.0-RC6",
    ivy"dev.zio::zio-json:0.6.2",
  )
}

object `alvarium-node` extends AlvariumModule

object sandbox extends AlvariumModule {
  override def otherDeps = Agg(
    ivy"dev.zio::zio:2.1-RC1",
    ivy"dev.zio::zio-streams:2.1-RC1",
    ivy"dev.zio::zio-http:3.0.0-RC6",
  )
}

