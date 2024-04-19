package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag

import scala.sys.exit


import zio._
import zio.http._
import zio.json.{DeriveJsonDecoder, JsonDecoder}

case class ImageRequest(seed: String, signature: String, id: String, imageB64: String)

given JsonDecoder[ImageRequest] = DeriveJsonDecoder.gen[ImageRequest]

def computeImage(request: Request) = ZIO.logSpan("Request processing time") {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString

    _ <- (ZIO.attempt {
      try
        sdk.transit(bag, data.getBytes())
      catch
        case e =>
          e.printStackTrace()
          exit()
    }).fork
  } yield Response(
    status = Status.Ok,
    body = request.body
  ))
    .catchAll(e => ZIO.logErrorCause(Cause.die(e)) &> ZIO.succeed(Response(Status.InternalServerError, body = Body.fromString("error!"))))
}


object worker extends ZIOAppDefault {
  val app = Routes(
    Method.GET / "compute" -> handler(computeImage(_)),
    Method.GET / "ping" -> handler(for {
      _ <- Console.printLine("Pinged").orDie
      chunk <- Random.nextBytes(2500)
    } yield Response(
      body = Body.fromChunk(chunk)
    ))
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
        "./config/config.json",
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