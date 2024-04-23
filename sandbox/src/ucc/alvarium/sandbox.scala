package ucc.alvarium

import com.alvarium.DefaultSdk
import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import org.apache.logging.log4j.LogManager
import zio.*
import zio.http.*
import zio.metrics.*
import ucc.alvarium.{config, PropertyBag as PBag}

object sandbox extends ZIOAppDefault {

  private val Url = URL(path = Path("ping"), kind = URL.Location.Absolute(Scheme.HTTP, "localhost", Some(8080)))

  private val NumberStr = "[0-9]+$".r

  def run =
    val counter = Metric.counterInt("zizi").fromConst(1)
    for {
      _ <- ZIO.unit @@ counter
      _ <- ZIO.unit @@ counter
      counter <- counter.value
      _ <- Console.printLine(s"counter : ${counter.count}")
    } yield ()


}

@main def main =

//  val process = new ProcessBuilder("ping", "mosquitto-server")
//    .inheritIO()
//    .start()

  val sdk = {
    val log = LogManager.getRootLogger
    val sdkInfo = config

    val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
    new DefaultSdk(annotators, sdkInfo, log)
  }

  val bag = PBag(
    AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
      "./config",
      "./config-dir-checksum.txt"
    ),
    AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
      "./config/config.json",
      "./config-file-checksum.txt"
    ),
  )

  println("Hello?")

  sdk.create(bag, Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

