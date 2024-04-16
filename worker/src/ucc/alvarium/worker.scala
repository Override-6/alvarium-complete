package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.json.{DeriveJsonDecoder, JsonDecoder}

case class ImageRequest(seed: String, signature: String, imageB64: String)

given JsonDecoder[ImageRequest] = DeriveJsonDecoder.gen[ImageRequest]

def computeImage(request: Request) = {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString
    request <- ZIO.fromEither(JsonDecoder[ImageRequest].decodeJson(data))
  } yield {
    sdk.transit(bag, data.getBytes())
  })
    .catchAll(e => ZIO.logError(e.toString) &> ZIO.succeed(Response(Status.InternalServerError)))
} &> ZIO.succeed(Response(
  status = Status.Ok,
  body = request.body
))

object worker extends ZIOAppDefault {
  val app = Routes(
    Method.GET / "compute" -> handler(computeImage(_))
  ).toHttpApp

  def run = {
    val sdkLayer = ZLayer.succeed {
      val log = LogManager.getRootLogger
      val config = os.read(os.pwd / "config" / "config.json")
      val sdkInfo = SdkInfo.fromJson(config)
      val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
      new DefaultSdk(annotators, sdkInfo, log)
    }

    val bag = ZLayer.succeed(PBag(
      AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
        "./config",
        "./config-dir-checksum.txt"
      ),
      AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
        "./config/",
        "./config-file-checksum.txt"
      ),
    ))

    val serverConfig = Server.default

    Server
      .serve(app)
      .provide(
        serverConfig,
        sdkLayer,
        bag
      )
  }
}