package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.stream.ZStream

case class ImageInfo(id: String, gender: String, masterCategory: String, subCategory: String, articleType: String, baseColour: String, season: String, year: Int, usage: String, productDisplayName: String)

val tcount = java.lang.Runtime.getRuntime.availableProcessors()

val images = (os.pwd / "data" / "images")

val Address = "alvarium-workers"

object master extends ZIOAppDefault {
  def run  = {
    val sdkLayer = ZLayer.succeed {
      val log = LogManager.getRootLogger
      val config = os.read(os.pwd / "config" / "config.json")
      val sdkInfo = SdkInfo.fromJson(config)
      val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
      new DefaultSdk(annotators, sdkInfo, log)
    }

    val bagLayer = ZLayer.succeed(PBag(
      AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
        "./config",
        "./config-dir-checksum.txt"
      ),
      AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
        "./config/",
        "./config-file-checksum.txt"
      ),
    ))

    val lines = os.read.lines(os.pwd / "data" / "styles.csv")

    val stream = for {
      args <- ZStream.service[ZIOAppArgs]
      response <- ZStream.fromIterable(lines)
        .mapZIOParUnordered(tcount)(s => ZIO.succeed(s.split(',')))
        .mapZIOParUnordered(tcount) {
          case Array(id, gender, masterCategory, subCategory, articleType, baseColour, season, year, usage, displayName) =>
            ZIO.succeed(ImageInfo(id, gender, masterCategory, subCategory, articleType, baseColour, season, year.toInt, usage, displayName))
        }
        .mapZIOParUnordered(tcount) { info =>
          for {
            sdk <- ZIO.service[Sdk]
            _ <- Console.printLine("sdk retrieved")
            body <- Body.fromFile((images / s"${info.id}.jpg").toIO)
            _ <- Console.printLine("Body got")
            response <- ZClient.request(Request(
              method = Method.GET,
              url = URL(Path(s"http://$Address:8080/compute")),
              body = body
            ))
            _ <- Console.printLine("Request sent")
          } yield response
        }
    } yield ()

    Console.printLine("Running") &> stream.runDrain
      .provideSome[ZIOAppArgs](
        sdkLayer,
        Scope.default,
        ZClient.default
      )
  }
}

