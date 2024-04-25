package ucc.alvarium

import zio.*
import zio.http.*
import zio.profiling.sampling.SamplingProfiler
import zio.stream.ZStream

object sandbox extends ZIOAppDefault {

  private val Url = URL(path = Path("ping"), kind = URL.Location.Absolute(Scheme.HTTP, "localhost", Some(8080)))

  private val NumberStr = "[0-9]+$".r


  private def work(n: Int) = ZStream.fromSchedule(Schedule.recurs(n))
    .foreach(idx => Console.printLine(idx))

  def run =
    val fast = work(500)
    val slow = work(1000)

    val program = fast <&> slow

    SamplingProfiler(1.nano)
      .profile(program)
      .flatMap(_.stackCollapseToFile("/tmp/test.folded"))
}

//@main def main =
//
//  //  val process = new ProcessBuilder("ping", "mosquitto-server")
//  //    .inheritIO()
//  //    .start()
//
//  val sdk = {
//    val log = LogManager.getRootLogger
//    val sdkInfo = mockConfig
//
//    val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
//    new DefaultSdk(annotators, sdkInfo, log)
//  }
//
//
//  val bag = PBag(
//    AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
//      "./config",
//      "./config-dir-checksum.txt"
//    ),
//    AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
//      "./config/config.json",
//      "./config-file-checksum.txt"
//    ),
//  )
//
//  val privateKey = os.read(os.pwd / "res" / "private.key")
//  val signer = new Ed25519Sign(Encoder.hexToBytes(privateKey).take(32))
//
//  val signature = Encoder.bytesToHex(signer.sign("123456".getBytes))
//
//  val publicKey = os.read(os.pwd / "res" / "public.key")
//
//  sdk.create(bag, s"""{"signature": "$signature", "seed": "123456"}""".getBytes())
//
//  val verifier = new Ed25519Verify(Encoder.hexToBytes(publicKey))
//  verifier.verify(signature.getBytes, "123456".getBytes())
//
//
