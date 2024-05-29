package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, EnvironmentCheckerEntry, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.PropertyBag
import com.alvarium.{DefaultSdk, Sdk}
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.json.{JsonDecoder, JsonEncoder}

import java.net.InetAddress
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.SSLSocket


val WorkersAddress = "alvarium-workers"
val StorageAddress = "alvarium-storage"
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTP, WorkersAddress, Some(8080)))
val StorageURL = URL(Path(s"storage"), URL.Location.Absolute(Scheme.HTTP, StorageAddress, Some(8080)))

val TLSDefectProbability = 0.25F
val SourceCodeDefectProbability = 0.25F
val ChecksumDefectProbability = 0.25F
val PKIDefectProbability = 0.25F / 5

def computeImage(request: Request) = ZIO.logSpan("Request processing time") {
  (for {
    sdk <- ZIO.service[Sdk]
    data <- request.body.asString
    sslSocket <- ZIO.service[SSLSocket]

    bag <- ZIO.service[PropertyBag]

    requestData <- ZIO.fromEither(JsonDecoder[ImageRequest].decodeJson(data))


    _ <- ZIO.attempt {
        sdk.transit(bag, requestData.imageB64.getBytes())
      }.catchAllCause(ZIO.logErrorCause(_))
      .forkDaemon

    _ <- Console.printLine(s"Transiting image ${requestData.id}, hops count : ${requestData.remainingHopCount}")

    _ <- ZIO.when(requestData.remainingHopCount > 0) {
      val newSeed = if probably(PKIDefectProbability) then "DEFECT SEED" else requestData.seed

      val requestDataNew = requestData.copy(remainingHopCount = requestData.remainingHopCount - 1, seed = newSeed)
      val json = JsonEncoder[ImageRequest].encodeJson(requestDataNew)
      val body = Body.fromCharSequence(json)
      val url = if requestDataNew.remainingHopCount == 0 then StorageURL else ComputeURL
      for {
        response <- ZIO.scoped {
          ZClient.request(Request(
            method = Method.GET,
            url = url,
            body = body
          ))
        }
      } yield ()
    }.catchAllCause(ZIO.logErrorCause(_)).forkDaemon


  } yield Response(
    status = Status.Ok,
    body = request.body
  ))
    .catchAllCause(e => ZIO.logErrorCause(e) &> ZIO.succeed(Response(Status.InternalServerError, body = Body.fromString("error!"))) *> ZIO.succeed { sys.exit(1) })
}


object worker extends ZIOAppDefault {
  val app = Routes(
    Method.GET / "compute" -> handler(computeImage(_)),
  ).toHttpApp

  java.lang.System.setProperty("java.net.preferIPv4Stack", "true")
  
  def run = {
    val sdkLayer = ZLayer.succeed {
      val log = LogManager.getRootLogger
      val sdkInfo = config

      val annotators = sdkInfo.getAnnotators
        .map(cfg => new EnvironmentCheckerEntry(cfg.getKind, new AnnotatorFactory().getAnnotator(cfg, sdkInfo, log)))
      new DefaultSdk(annotators, sdkInfo, log)
    }

    val sslServerSocket = getServerSocket

    Thread.startVirtualThread { () =>
      while (true) {
        val socket = sslServerSocket.accept()
        socket.setKeepAlive(true)
        socket.getOutputStream.write(Array(1.toByte))
        socket.getOutputStream.flush()
      }
    }

    val sslSocket = getClientSocket(WorkersAddress)
    sslSocket.getInputStream.read()

    val bag = ZLayer.succeed(PBag(
      AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
        "./config",
        if probably(SourceCodeDefectProbability) then "res/config-dir-checksum-defect.txt" else "res/config-dir-checksum.txt"
      ),
      AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
        "./config/config.json",
        if probably(ChecksumDefectProbability) then "res/config-file-checksum-defect.txt" else "res/config-file-checksum.txt"
      ),
      AnnotationType.TLS -> sslSocket
    ))

    java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
      sslSocket.close()
      sslServerSocket.close()
    }))

    for {
      _ <- ZIO.when(probably(TLSDefectProbability)) {
        ZIO.attempt {
          sslSocket.close()
        }
      }
      _ <- Server
        .serve(app)
        .provide(
          Server.default,
          Client.default,
          bag,
          sdkLayer,
          ZLayer.succeed(sslSocket)
        )
    } yield ()
  }
}

def bag(imageId: String) = for {
  sslSocket <- ZIO.service[SSLSocket]
} yield PBag(
  AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
    "./config",
    if probably(SourceCodeDefectProbability) then "res/config-dir-checksum-defect.txt" else "res/config-dir-checksum.txt"
  ),
  AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
    "./config/config.json",
    if probably(ChecksumDefectProbability) then "res/config-file-checksum-defect.txt" else "res/config-file-checksum.txt"
  ),
  AnnotationType.TLS -> sslSocket
)

def probably(f: Float): Boolean = ThreadLocalRandom.current().nextFloat() <= f