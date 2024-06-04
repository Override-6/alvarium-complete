//package ucc.alvarium
//
//import com.alvarium.DefaultSdk
//import com.alvarium.annotators.{EnvironmentCheckerEntry, EnvironmentCheckerFactory}
////import com.alvarium.annotators.AnnotatorFactory
//import com.alvarium.contracts.AnnotationType
//import com.alvarium.utils.{Encoder, PropertyBag}
//import com.google.crypto.tink.PublicKeySign
//import com.google.crypto.tink.subtle.Ed25519Sign
//import org.apache.logging.log4j.LogManager
//import ucc.alvarium.PropertyBag as PBag
//import zio.json.JsonEncoder
//
//import java.time.{Duration, Instant}
//
//
//val ImagesDir = os.pwd / "data" / "images"
//
//val Address = "alvarium-workers"
//val NumberPattern = "([0-9]+)".r
//
//
//@main def bench = {
//  val privateKey = os.read(os.pwd / "res" / "private.key")
//
//  given PublicKeySign = new Ed25519Sign(Encoder.hexToBytes(privateKey).take(32))
//
//  given PropertyBag = PBag(
//    //    AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
//    //      "./config",
//    //      "res/config-dir-checksum.txt"
//    //    ),
//    //    AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
//    //      "./config/config.json",
//    //      "res/config-file-checksum.txt"
//    //    ),
//    //    AnnotationType.TLS -> sslSocket
//  )
//
//  runBench(5000, 20000)()
//  runBench(5000, 20000)(AnnotationType.SOURCE)
//  runBench(5000, 20000)((AnnotationType.SOURCE :: Nil) * 3 *)
//  runBench(5000, 20000)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 20000)((AnnotationType.SOURCE :: Nil) * 7 *)
//  runBench(5000, 20000)((AnnotationType.SOURCE :: Nil) * 10 *)
//  println("---")
//
//  runBench(5000, 0)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 1000)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 5000)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 10000)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 20000)((AnnotationType.SOURCE :: Nil) * 5 *)
//  runBench(5000, 80000)((AnnotationType.SOURCE :: Nil) * 5 *)
//
//  println("END")
//}
//
//extension [A](that: Seq[A])
//  def *(n: Int): Seq[A] = (1 to n).flatMap(_ => that)
//
//
//def runBench(iterationCount: Int, dataSize: Int)(annotations: AnnotationType*)(using bag: PropertyBag, signer: PublicKeySign): Unit = {
//  val request = provideRequest("0", new Array(dataSize), signer)
//  val json = JsonEncoder[ImageRequest].encodeJson(request)
//  val bytes = json.toString.getBytes()
//
//  val sdk = {
//    val log = LogManager.getRootLogger
//    val sdkInfo = config(annotations *)
//    val annotators = sdkInfo.getAnnotators
//      .map(cfg => new EnvironmentCheckerEntry(cfg.getKind(), EnvironmentCheckerFactory.getChecker(cfg, sdkInfo, log, null)))
//    //    val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
//    new DefaultSdk(annotators, sdkInfo, log)
//  }
//
//  measure(s"iterations : $iterationCount, bytes: $dataSize, annotations count : ${annotations.length}", 10) {
//    for (i <- 0 to iterationCount) {
//      //      if (i % 1000 == 0)
//      //        println(i)
//      sdk.create(bag, bytes)
//    }
//  }
//}
//
//def measure(label: String, count: Int)(f: => Unit): Unit = {
//  val tries = for (i <- 1 to count) yield {
//    System.gc()
//    val t0 = Instant.now()
//    f
//    val t1 = Instant.now()
//    val duration = Duration.between(t0, t1)
//    duration.toMillis
//  }
//  val min = Duration.ofMillis(tries.min)
//  val max = Duration.ofMillis(tries.max)
//  val avg = Duration.ofMillis(tries.sum / tries.size)
//  println(s"$label - min: $min avg: $avg max: $max")
//}
//
//def provideRequest(id: String, fileContent: Array[Byte], signer: PublicKeySign) = {
//  val content = Encoder.bytesToHex(fileContent)
//  val seed = content.hashCode.toString
//
//  val signature = signer.sign(seed.getBytes)
//  ImageRequest(seed, Encoder.bytesToHex(signature), 5, id, "label", content)
//}
//
//
