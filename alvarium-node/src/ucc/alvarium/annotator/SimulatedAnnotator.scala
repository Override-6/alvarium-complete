package ucc.alvarium.annotator

import com.alvarium.annotators.{Annotator, AnnotatorException}
import com.alvarium.contracts.{Annotation, AnnotationType, LayerType}
import com.alvarium.hash.{HashProvider, HashProviderFactory, HashType, HashTypeException}
import com.alvarium.sign.{KeyInfo, SignException, SignProvider, SignProviderFactory, SignatureInfo}
import com.alvarium.utils.{Encoder, PropertyBag}
import org.apache.logging.log4j.Logger

import java.io.IOException
import java.net.{InetAddress, UnknownHostException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.Instant

class SimulatedAnnotator(hash: HashType,
                         signatureInfo: SignatureInfo,
                         logger: Logger,
                         layer: LayerType,
                         kind: AnnotationType,
                         isSatisfied: => Boolean
                        ) extends Annotator {

  private val hostName = InetAddress.getLocalHost.getHostName

  override def execute(ctx: PropertyBag, data: Array[Byte]): Annotation = {
    val key = deriveHash(hash, data)

    val annotation = Annotation(
      key,
      hash,
      hostName,
      layer,
      kind,
      null,
      isSatisfied,
      Instant.now()
    )
    signAnnotation(signatureInfo.getPublicKey, annotation)
    annotation
  }

  /**
   * returns hash of the provided data depending on the given hash type
   *
   * @param type
   * @param data
   * @return
   * @throws AnnotatorException
   */
  @throws[AnnotatorException]
  private def deriveHash(`type`: HashType, data: Array[Byte]): String = {
    val hashFactory = new HashProviderFactory
    try {
      val provider = hashFactory.getProvider(`type`)
      provider.derive(data)
    } catch {
      case e: HashTypeException =>
        throw new AnnotatorException("cannot hash data.", e)
    }
  }

  /**
   * returns the signature of the given annotation object after converting it to its json
   * representation
   *
   * @param keyInfo
   * @param annotation
   * @return
   * @throws AnnotatorException
   */
  @throws[AnnotatorException]
  private def signAnnotation(keyInfo: KeyInfo, annotation: Annotation) = {
    val signFactory = new SignProviderFactory
    try {
      val provider = signFactory.getProvider(keyInfo.getType)
      val key = Files.readString(Paths.get(keyInfo.getPath), StandardCharsets.US_ASCII)
      val signature = provider.sign(Encoder.hexToBytes(key), annotation.toJson.getBytes)
      annotation.setSignature(signature)
    } catch {
      case e: SignException =>
        throw new AnnotatorException("cannot sign annotation.", e)
      case e: IOException =>
        throw new AnnotatorException("cannot read key.", e)
    }
  }
}
