package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.metrics.Metric

import javax.net.ssl.SSLSocket

val transitCounter = Metric.counter("transit counter").fromConst(1)

val Address = "alvarium-workers"
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTP, Address, Some(8080)))

def computeImage(request: Request) = ZIO.logSpan("Request processing time") {
  (for {
    sdk <- ZIO.service[Sdk]
    bag <- ZIO.service[PropertyBag]
    data <- request.body.asString
    sslSocket <- ZIO.service[SSLSocket]

    _ <- ZIO.attempt {
        println("session : " + sslSocket.getSession)
        println("is closed : " + sslSocket.isClosed)
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
      val sdkInfo = config

      val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
      new DefaultSdk(annotators, sdkInfo, log)
    }

    val sslServerSocket = getServerSocket

    new Thread(() => while (true) {
      println("Accepting connexion...")
      val socket = sslServerSocket.accept()

      println("Accepted")
      socket.setKeepAlive(true)
      socket.getOutputStream.write(Array(1.toByte))
      socket.getOutputStream.flush()
    }).start()

    val sslSocket = getClientSocket(Address)
    sslSocket.getInputStream.read()

    val bag = ZLayer.succeed(PBag(
      AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
        "./config",
        "./config-dir-checksum.txt"
      ),
      AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
        "./config/config.json",
        "./config-file-checksum.txt"
      ),
      AnnotationType.TLS -> sslSocket
    ))

    java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
      sslSocket.close()
      sslServerSocket.close()
    }))

    Console.printLine("Server launched") *> Server
      .serve(app)
      .provide(
        Server.default,
        Client.default,
        sdkLayer,
        bag,
        ZLayer.succeed(sslSocket)
      )
  }
}