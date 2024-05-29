package ucc.alvarium

import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, EnvironmentCheckerEntry, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.{Encoder, PropertyBag}
import com.alvarium.{DefaultSdk, Sdk}
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.subtle.Ed25519Sign
import io.netty.util.internal.ThreadLocalRandom
import org.apache.logging.log4j.LogManager
import ucc.alvarium.PropertyBag as PBag
import zio.*
import zio.http.*
import zio.json.JsonEncoder
import zio.metrics.Metric
import zio.stream.ZStream

import scala.jdk.CollectionConverters.*


case class ImageInfo(id: String, gender: String, masterCategory: String, subCategory: String, articleType: String, baseColour: String, season: String, year: Int, usage: String, productDisplayName: String)


val ImagesDir = os.pwd / "data" / "images"

val Address = "alvarium-workers"
val NumberPattern = "([0-9]+)".r
val ComputeURL = URL(Path(s"compute"), URL.Location.Absolute(Scheme.HTTP, Address, Some(8080)))

val classes = Array(
  "Shirts",
  "Jeans",
  "Watches",
  "Track Pants",
  "Tshirts",
  "Socks",
  "Casual Shoes",
  "Belts",
  "Flip Flops",
  "Handbags",
  "Tops",
  "Bra",
  "Sandals",
  "Shoe Accessories",
  "Sweatshirts",
  "Deodorant",
  "Formal Shoes",
  "Bracelet",
  "Lipstick",
  "Flats",
  "Kurtas",
  "Waistcoat",
  "Sports Shoes",
  "Shorts",
  "Briefs",
  "Sarees",
  "Perfume and Body Mist",
  "Heels",
  "Sunglasses",
  "Innerwear Vests",
  "Pendant",
  "Nail Polish",
  "Laptop Bag",
  "Scarves",
  "Rain Jacket",
  "Dresses",
  "Night suits",
  "Skirts",
  "Wallets",
  "Blazers",
  "Ring",
  "Kurta Sets",
  "Clutches",
  "Shrug",
  "Backpacks",
  "Caps",
  "Trousers",
  "Earrings",
  "Camisoles",
  "Boxers",
  "Jewellery Set",
  "Dupatta",
  "Capris",
  "Lip Gloss",
  "Bath Robe",
  "Mufflers",
  "Tunics",
  "Jackets",
  "Trunk",
  "Lounge Pants",
  "Face Wash and Cleanser",
  "Necklace and Chains",
  "Duffel Bag",
  "Sports Sandals",
  "Foundation and Primer",
  "Sweaters",
  "Free Gifts",
  "Trolley Bag",
  "Tracksuits",
  "Swimwear",
  "Shoe Laces",
  "Fragrance Gift Set",
  "Bangle",
  "Nightdress",
  "Ties",
  "Baby Dolls",
  "Leggings",
  "Highlighter and Blush",
  "Travel Accessory",
  "Kurtis",
  "Mobile Pouch",
  "Messenger Bag",
  "Lip Care",
  "Face Moisturisers",
  "Compact",
  "Eye Cream",
  "Accessory Gift Set",
  "Beauty Accessory",
  "Jumpsuit",
  "Kajal and Eyeliner",
  "Water Bottle",
  "Suspenders",
  "Lip Liner",
  "Robe",
  "Salwar and Dupatta",
  "Patiala",
  "Stockings",
  "Eyeshadow",
  "Headband",
  "Tights",
  "Nail Essentials",
  "Churidar",
  "Lounge Tshirts",
  "Face Scrub and Exfoliator",
  "Lounge Shorts",
  "Gloves",
  "Mask and Peel",
  "Wristbands",
  "Tablet Sleeve",
  "Ties and Cufflinks",
  "Footballs",
  "Stoles",
  "Shapewear",
  "Nehru Jackets",
  "Salwar",
  "Cufflinks",
  "Jeggings",
  "Hair Colour",
  "Concealer",
  "Rompers",
  "Body Lotion",
  "Sunscreen",
  "Booties",
  "Waist Pouch",
  "Hair Accessory",
  "Rucksacks",
  "Basketballs",
  "Lehenga Choli",
  "Clothing Set",
  "Mascara",
  "Toner",
  "Cushion Covers",
  "Key chain",
  "Makeup Remover",
  "Lip Plumper",
  "Umbrellas",
  "Face Serum and Gel",
  "Hat",
  "Mens Grooming Kit",
  "Rain Trousers"
)

val TCount = java.lang.Runtime.getRuntime.availableProcessors()


def streamPipeline(lines: Seq[String], untrusted: Boolean) = {
  val sdkLayer = ZLayer.succeed {
    val log = LogManager.getRootLogger
    val sdkInfo = config
    val annotators = sdkInfo.getAnnotators
      .map(cfg => new EnvironmentCheckerEntry(cfg.getKind, new AnnotatorFactory().getAnnotator(cfg, sdkInfo, log)))
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

  //  val pipeline = ZStream.fromSchedule(
  //      Schedule.fixed(10.seconds)
  //        .map(i => lines(i.toInt))
  //    )
  val pipeline = ZStream.fromIterable(lines)
    .mapZIOParUnordered(TCount)(s => ZIO.succeed(s.split(',')))
    .mapZIOParUnordered(TCount) {
      case Array(id, gender, masterCategory, subCategory, articleType, baseColour, season, NumberPattern(year), usage, displayName) if os.exists(ImagesDir / s"$id.jpg") =>
        ZIO.some(ImageInfo(id, gender, masterCategory, subCategory, articleType, baseColour, season, year.toInt, usage, displayName)) @@ okCounter
      case other => ZIO.logError(s"cannot compute image ${other.mkString("Array(", ", ", ")")}") &> ZIO.none @@ errCounter
    }
    .collectSome
    .mapZIOParUnordered(TCount) { info =>
      (for {
        sdk <- ZIO.service[Sdk]
        bag <- ZIO.service[PropertyBag]

        fileContent = os.read.bytes(ImagesDir / s"${info.id}.jpg") //os.read.stream(ImagesDir / s"${info.id}.jpg").readBytesThrough(_.readNBytes(1000))
        request = provideRequest(info.id, if (untrusted) classes(ThreadLocalRandom.current().nextInt(classes.length)) else info.articleType, fileContent, signer)
        json = JsonEncoder[ImageRequest].encodeJson(request)
        body = Body.fromCharSequence(json)

        _ <- ZIO.attempt {
          sdk.create(bag, request.imageB64.getBytes)
        }

        response <- ZIO.scoped {
          ZClient.request(Request(
            method = Method.GET,
            url = ComputeURL,
            body = body
          ))
        } @@ reqCounter
      } yield ()).catchAllCause(ZIO.logErrorCause(_) @@ errCounter)
    }

  for {
    _ <- ZIO.when(!untrusted) {
      ZIO.attempt {
        sslSocket.close()
      }
    }
    _ <- pipeline
      .runDrain
      .provide(
        sdkLayer,
        bagLayer,
        Client.default,
      )
    ok <- okCounter.value
    err <- errCounter.value
    req <- reqCounter.value
    _ <- Console.printLine(s"Counters : ok: ${ok.count} err: ${err.count} requests: ${req.count}")
    _ <- Console.printLine(s"total lines : ${lines.size}")
  } yield ()
}


def provideRequest(id: String, className: String, fileContent: Array[Byte], signer: PublicKeySign) = {
  val content = Encoder.bytesToHex(fileContent)
  val seed = content.hashCode.toString

  val signature = signer.sign(seed.getBytes)
  ImageRequest(seed, Encoder.bytesToHex(signature), 5, className, id, content)
}
