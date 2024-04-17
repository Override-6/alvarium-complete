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

import scala.util.Try

case class ImageRequest(seed: String, signature: String, imageB64: String)

given JsonDecoder[ImageRequest] = DeriveJsonDecoder.gen[ImageRequest]

def computeImage(request: Request) = {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString
    request <- ZIO.fromEither(JsonDecoder[ImageRequest].decodeJson(data)).catchAll(ZIO.dieMessage(_))
    _ <- Console.printLine(s"processing image...")
    _ <- ZIO.attempt {
      println("transiting...")
      sdk.transit(bag, data.getBytes())
      println("transit done!")
    }
  } yield ())
    .catchAll(e => ZIO.logErrorCause(Cause.die(e)) &> ZIO.succeed(Response(Status.InternalServerError, body = Body.fromString("error!"))))
} *> ZIO.succeed(Response(
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