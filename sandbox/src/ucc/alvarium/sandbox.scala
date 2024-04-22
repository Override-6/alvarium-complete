import zio.*
import zio.http.*
import zio.metrics.*

object sandbox extends ZIOAppDefault {

  val url = URL(path = Path("ping"), kind = URL.Location.Absolute(Scheme.HTTP, "localhost", Some(8080)))

  val NumberStr = "[0-9]+$".r

  def run =
    val counter = Metric.counterInt("zizi").fromConst(1)
    for {
      _ <- ZIO.unit @@ counter
      _ <- ZIO.unit @@ counter
      counter <- counter.value
      _ <- Console.printLine(s"counter : ${counter.count}")
    } yield ()


}