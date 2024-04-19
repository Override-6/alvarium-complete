import zio._
import zio.http._

object sandbox extends ZIOAppDefault {

  val url = URL(path = Path("ping"), kind = URL.Location.Absolute(Scheme.HTTP, "localhost", Some(8080)))

  val NumberStr = "[0-9]+$".r

  def run = ZIO.attempt {
    "78" match {
      case NumberStr(str) => println(s"number $str")
      case _ => println("NaN")
    }
  }


}