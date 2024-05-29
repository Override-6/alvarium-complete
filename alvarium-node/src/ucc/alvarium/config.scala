package ucc.alvarium

import com.alvarium.SdkInfo
import com.alvarium.annotators.AnnotatorConfig
import com.alvarium.contracts.{AnnotationType, LayerType}
import com.alvarium.hash.{HashInfo, HashType}
import com.alvarium.sign.{KeyInfo, SignType, SignatureInfo}
import com.alvarium.streams.{MqttConfig, StreamInfo, StreamType}
import com.alvarium.utils.ServiceInfo

import java.util.UUID

val AllSupportedAnnotations = Seq(
  AnnotationType.SourceCode,
  AnnotationType.CHECKSUM,
  AnnotationType.SOURCE,
  AnnotationType.TLS,
  AnnotationType.TPM,
)

def config(annotations: AnnotationType*): SdkInfo = makeConfig(
  new StreamInfo(
    StreamType.MQTT,
    new MqttConfig(
      UUID.randomUUID().toString,
      "mosquitto",
      "",
      0,
      false,
      Array("alvarium-topic"),
      new ServiceInfo(
        "mosquitto-server",
        "tcp",
        1883
      )
    )
  ),
  annotations *
)

def config: SdkInfo = config(
  AllSupportedAnnotations *
)

def mockConfig(annotations: AnnotationType*): SdkInfo = makeConfig(
  new StreamInfo(
    StreamType.MOCK,
    null
  ),
  annotations *
)

def mockConfig: SdkInfo = mockConfig(
  AllSupportedAnnotations *
)

def makeConfig(stream: StreamInfo, annotations: AnnotationType*): SdkInfo = new SdkInfo(
  annotations.map(new AnnotatorConfig(_)).toArray,

  new HashInfo(
    HashType.SHA256Hash
  ),

  new SignatureInfo(
    new KeyInfo(
      "./res/public.key",
      SignType.Ed25519
    ),
    new KeyInfo(
      "./res/private.key",
      SignType.Ed25519
    )
  ),

  stream,

  LayerType.Application
)