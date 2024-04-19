package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import com.alvarium.utils.PropertyBag
import ucc.alvarium.PropertyBag as PBag
import org.apache.logging.log4j.LogManager
import zio.{Random, ZIO, ZLayer}
import zio.http.{Body, Method, Path, Request, Scheme, URL, ZClient}
import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.stream.ZStream

import java.util.Base64


case class ImageInfo(id: String, gender: String, masterCategory: String, subCategory: String, articleType: String, baseColour: String, season: String, year: Int, usage: String, productDisplayName: String)

val TCount = java.lang.Runtime.getRuntime.availableProcessors()

val ImagesDir = (os.pwd / "data" / "images")

val Address = "localhost"
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTP, Address, Some(8080)))
val NumberPattern = "([0-9]+)".r

case class ImageRequest(seed: String, signature: String, id: String, imageB64: String)

given JsonEncoder[ImageRequest] = DeriveJsonEncoder.gen[ImageRequest]

def streamPipeline(lines: Iterable[String]) = {

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
      "./config/config.json",
      "./config-file-checksum.txt"
    ),
  ))

  val pipeline = for {
    response <- ZStream.fromIterable(lines)
      .mapZIOParUnordered(TCount)(s => ZIO.succeed(s.split(',')))
      .mapZIOParUnordered(TCount) {
        case Array(id, gender, masterCategory, subCategory, articleType, baseColour, season, NumberPattern(year), usage, displayName) =>
          ZIO.some(ImageInfo(id, gender, masterCategory, subCategory, articleType, baseColour, season, year.toInt, usage, displayName))
        case other => ZIO.logError(s"cannot compute image ${other.mkString("Array(", ", ", ")")}") *> ZIO.none
      }
      .collectSome
      .mapZIOParUnordered(TCount) { info =>
        for {
          sdk <- ZIO.service[Sdk]
          bag <- ZIO.service[PropertyBag]

          bytesChunk <- Random.nextBytes(0)
          bytes <- Body.fromChunk(bytesChunk).asArray

          fileContent = bytes //os.read.bytes(ImagesDir / s"${info.id}.jpg")
          json = JsonEncoder[ImageRequest].encodeJson(ImageRequest("seed", "signature", info.id, Base64.getEncoder.encodeToString(fileContent)))
          body = Body.fromCharSequence(json)

          //        _ <- ZIO.attempt {
          //          sdk.create(bag, json.toString.getBytes)
          //        }
        } yield Request(
          method = Method.GET,
          url = ComputeURL,
          body = body
        ) -> info.id
      }
      .mapZIOParUnordered(TCount) {
        case (r, id) => ZIO.logSpan("Request latency") {
          ZIO.scoped {
            ZClient.request(r)//.tap(r => ZIO.log(s"Received response for image $id $r"))
          }
        }
      }
  } yield ()

  pipeline
    .runDrain
    .provide(sdkLayer, bagLayer, ZClient.default)
}
