package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.streams.MqttConfig
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk, SdkInfo}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.{config, mockConfig, PropertyBag as PBag}

import scala.sys.exit
import zio.*
import zio.http.*
import zio.http.Server.{Config, live}
import zio.json.{DeriveJsonDecoder, JsonDecoder, JsonEncoder}
import zio.metrics.Metric

import java.util.Base64

val transitCounter = Metric.counter("transit counter").fromConst(1)

val Address = "alvarium-workers"
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTPS, Address, Some(8080)))

def computeImage(request: Request) = ZIO.logSpan("Request processing time") {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString

    _ <- ZIO.attempt {
        sdk.transit(bag, data.getBytes())
      }.catchAllCause(ZIO.logErrorCause(_))
      .forkDaemon
    
    requestData <- ZIO.from(JsonDecoder[ImageRequest].decodeJson(data))
    _ <- ZIO.when(requestData.remainingHopCount > 0) {
      val requestDataNew = requestData.copy(remainingHopCount = requestData.remainingHopCount - 1)
      val json = JsonEncoder[ImageRequest].encodeJson(requestDataNew)
      val body = Body.fromCharSequence(json)
      ZIO.scoped {
        ZClient.request(Request(
          method = Method.GET,
          url = ComputeURL,
          body = body
        ))
      }
    }.forkDaemon


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
      val sdkInfo = mockConfig

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

    val serverConfig = ZLayer.succeed(
      Server.Config.default.ssl(SSLConfig.fromResource(
        behaviour = SSLConfig.HttpBehaviour.Accept,
        certPath = "./res/domain.csr",
        keyPath = "./res/domain.key"
      ))
    ) >>> live
    val clientConfig = Client.default

    Server
      .serve(app)
      .provide(
        serverConfig,
        clientConfig,
        sdkLayer,
        bag
      )
  }
}