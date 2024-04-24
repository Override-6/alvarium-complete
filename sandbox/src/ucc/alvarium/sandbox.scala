package ucc.alvarium

import com.alvarium.DefaultSdk
import com.alvarium.annotators.{AnnotatorFactory, ChecksumAnnotatorProps, SourceCodeAnnotatorProps}
import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.Encoder
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.subtle.{Ed25519Sign, Ed25519Verify}
import org.apache.logging.log4j.LogManager
import zio.*
import zio.http.*
import zio.metrics.*
import ucc.alvarium.{mockConfig, PropertyBag as PBag}

import java.net.{InetAddress, Socket}
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

object sandbox extends ZIOAppDefault {

  private val Url = URL(path = Path("ping"), kind = URL.Location.Absolute(Scheme.HTTP, "localhost", Some(8080)))

  private val NumberStr = "[0-9]+$".r

  def run =
    val counter = Metric.counterInt("zizi").fromConst(1)
    (for {
      response <- Client.request(Request(method = Method.GET, url = Url))
      data <- response.body.asString
      _ <- Console.printLine(data)
    } yield ())
      .provide(Scope.default, Client.default)
}

@main def main =

//  val process = new ProcessBuilder("ping", "mosquitto-server")
//    .inheritIO()
//    .start()

  val sdk = {
    val log = LogManager.getRootLogger
    val sdkInfo = mockConfig

    val annotators = sdkInfo.getAnnotators.map(new AnnotatorFactory().getAnnotator(_, sdkInfo, log))
    new DefaultSdk(annotators, sdkInfo, log)
  }



  val bag = PBag(
    AnnotationType.SourceCode -> new SourceCodeAnnotatorProps(
      "./config",
      "./config-dir-checksum.txt"
    ),
    AnnotationType.CHECKSUM -> new ChecksumAnnotatorProps(
      "./config/config.json",
      "./config-file-checksum.txt"
    ),
  )

  val privateKey = os.read(os.pwd / "res" / "private.key")
  val signer = new Ed25519Sign(Encoder.hexToBytes(privateKey).take(32))
  
  val signature = Encoder.bytesToHex(signer.sign("123456".getBytes))

  val publicKey = os.read(os.pwd / "res" / "public.key")
  
  sdk.create(bag, s"""{"signature": "$signature", "seed": "123456"}""".getBytes())
  
  val verifier = new Ed25519Verify(Encoder.hexToBytes(publicKey))
  verifier.verify(signature.getBytes, "123456".getBytes())


