package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.streams.MqttConfig
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.{PropertyBag as PBag, config}

import scala.sys.exit
import zio.*
import zio.http.*
import zio.json.{DeriveJsonDecoder, JsonDecoder}
import zio.metrics.Metric

case class ImageRequest(seed: String, signature: String, id: String, imageB64: String)

given JsonDecoder[ImageRequest] = DeriveJsonDecoder.gen[ImageRequest]

val transitCounter = Metric.counter("transit counter").fromConst(1)

def computeImage(request: Request) = ZIO.logSpan("Request processing time") {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString

    counter <- transitCounter.value
    _ <- (ZIO.attempt {
      sdk.transit(bag, data.getBytes())
    }.catchAllCause(ZIO.logErrorCause(_)) *> (Console.printLine(s"transited data (counter = ${counter.count})") @@ transitCounter))
      .forkDaemon
  } yield Response(
    status = Status.Ok,
    body = request.body
  ))
    .catchAllCause(e => ZIO.logErrorCause(e) &> ZIO.succeed(Response(Status.InternalServerError, body = Body.fromString("error!"))))
}


object worker extends ZIOAppDefault {
  val app = Routes(
    Method.GET / "compute" -> handler(computeImage(_)),
    //    Method.GET / "ping" -> handler(for {
    //      _ <- Console.printLine("Pinged").orDie
    //      chunk <- Random.nextBytes(2500)
    //    } yield Response(
    //      body = Body.fromChunk(chunk)
    //    ))
  ).toHttpApp

  def run = {
    val sdkLayer = ZLayer.succeed {
      val log = LogManager.getRootLogger
      val sdkInfo = config
      
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