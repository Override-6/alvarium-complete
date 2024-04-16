package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.config.Configurator
import ucc.alvarium.PropertyBag as PBag

import javax.net.ssl.SSLSocketFactory
import zio.*
import zio.http.{URL, *}
import zio.stream.{ZSink, ZStream}

case class ImageInfo(id: String, gender: String, masterCategory: String, subCategory: String, articleType: String, baseColour: String, season: String, year: Int, usage: String, productDisplayName: String)

val tcount = java.lang.Runtime.getRuntime.availableProcessors()

val images = os.pwd / "data" / "images"


object master extends ZIOAppDefault {
  def run: RIO[ZIOAppArgs, Unit] = {
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

    os.walk(os.pwd).foreach(println)
    println("data : ")
    os.walk(os.pwd / "data").foreach(println)

    val lines = os.read.lines(os.pwd / "data" / "styles.csv")



    val stream = for {
      args <- ZStream.service[ZIOAppArgs]
      address = args.getArgs.head
      response <- ZStream.fromIterable(lines)
        .mapZIOParUnordered(tcount)(s => ZIO.succeed(s.split(',')))
        .mapZIOParUnordered(tcount) {
          case Array(id, gender, masterCategory, subCategory, articleType, baseColour, season, year, usage, displayName) =>
            ZIO.succeed(ImageInfo(id, gender, masterCategory, subCategory, articleType, baseColour, season, year.toInt, usage, displayName))
        }
        .mapZIOParUnordered(tcount) { info =>
          for {
            sdk <- ZIO.service[Sdk]
            body <- Body.fromFile((images / s"${info.id}.jpg").toIO)
            response <- ZClient.request(Request(
              method = Method.GET,
              url = URL(Path(s"http://$address:8080/compute")),
              body = body
            ))
          } yield response
        }
    } yield ()

    stream.runDrain
      .provideSome[ZIOAppArgs](
        sdkLayer,
        Scope.default,
        ZClient.default
      )
  }
}

