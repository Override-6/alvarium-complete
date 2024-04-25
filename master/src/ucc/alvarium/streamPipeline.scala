package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.{Encoder, PropertyBag}
import com.alvarium.{DefaultSdk, Sdk}
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.subtle.Ed25519Sign
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.json.JsonEncoder
import zio.metrics.Metric
import zio.stream.ZStream


case class ImageInfo(id: String, gender: String, masterCategory: String, subCategory: String, articleType: String, baseColour: String, season: String, year: Int, usage: String, productDisplayName: String)

val TCount = java.lang.Runtime.getRuntime.availableProcessors()

val ImagesDir = os.pwd / "data" / "images"

val Address = "alvarium-workers"
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTP, Address, Some(8080)))
val NumberPattern = "([0-9]+)".r


def streamPipeline(lines: Iterable[String]) = {

//  java.lang.System.setProperty("javax.net.debug", "ssl")

  val sdkLayer = ZLayer.succeed {
    val log = LogManager.getRootLogger
    val sdkInfo = config
    val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
    new DefaultSdk(annotators, sdkInfo, log)
  }

  val privateKey = os.read(os.pwd / "res" / "private.key")
  val signer = new Ed25519Sign(Encoder.hexToBytes(privateKey).take(32))

  val sslSocket = getClientSocket(Address)
  sslSocket.getInputStream.read()

  val bagLayer = ZLayer.succeed(PBag(
    AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
      "./config",
      "res/config-dir-checksum.txt"
    ),
    AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
      "./config/config.json",
      "res/config-file-checksum.txt"
    ),
    AnnotationType.TLS -> sslSocket
  ))

  val okCounter = Metric.counter("OK counter").fromConst(1)
  val errCounter = Metric.counter("ERR counter").fromConst(1)
  val reqCounter = Metric.counter("Request counter").fromConst(1)

  val pipeline = ZStream.fromIterable(lines)
    .mapZIOParUnordered(TCount)(s => ZIO.succeed(s.split(',')))
    .mapZIOParUnordered(TCount) {
      case Array(id, gender, masterCategory, subCategory, articleType, baseColour, season, NumberPattern(year), usage, displayName) =>
        ZIO.some(ImageInfo(id, gender, masterCategory, subCategory, articleType, baseColour, season, year.toInt, usage, displayName)) @@ okCounter
      case other => ZIO.logError(s"cannot compute image ${other.mkString("Array(", ", ", ")")}") &> ZIO.none @@ errCounter
    }
    .collectSome
    .mapZIOParUnordered(TCount) { info =>
      for {
        sdk <- ZIO.service[Sdk]
        bag <- ZIO.service[PropertyBag]

        bytesChunk <- Random.nextBytes(0)
        bytes <- Body.fromChunk(bytesChunk).asArray

        fileContent = bytes //os.read.bytes(ImagesDir / s"${info.id}.jpg")
        request = provideRequest(info, fileContent, signer)
        json = JsonEncoder[ImageRequest].encodeJson(request)
        body = Body.fromCharSequence(json)

        _ <- ZIO.attempt {
          sdk.create(bag, json.toString.getBytes)
        }.catchAllCause(ZIO.logErrorCause(_)).forkDaemon
      } yield Request(
        method = Method.GET,
        url = ComputeURL,
        body = body
      ) -> info.id
    }
    .mapZIOParUnordered(TCount) {
      case (r, id) => ZIO.logSpan("Request latency") {
        ZIO.scoped {
          ZClient.request(r).catchAllCause(ZIO.logErrorCause(_)) //.tap(r => ZIO.log(s"Received response for image $id $r"))
        } @@ reqCounter
      }
    }

//  val clientLayer = ZLayer.succeed(
//    ZClient.Config.default.ssl(ClientSSLConfig.FromTrustStoreResource(
//    "./res/client.truststore",
//    "123456"
//  )))



  for {
    _ <- pipeline
      .runDrain
      .provide(
        sdkLayer,
        bagLayer,
        Client.default,
//        Client.customized,
//        NettyClientDriver.live,
//        DnsResolver.default,
//        ZLayer.succeed(NettyConfig.default),
      )
    ok <- okCounter.value
    err <- errCounter.value
    req <- reqCounter.value
    _ <- Console.printLine(s"Counters : ok: ${ok.count} err: ${err.count} requests: ${req.count}")
    _ <- Console.printLine(s"total lines : ${lines.size}")
  } yield ()
}


def provideRequest(info: ImageInfo, fileContent: Array[Byte], signer: PublicKeySign) = {
  val content = Encoder.bytesToHex(fileContent)
  val seed = content.hashCode.toString

  val signature = signer.sign(seed.getBytes)
  ImageRequest(seed, Encoder.bytesToHex(signature), 5, info.id, content)
}
