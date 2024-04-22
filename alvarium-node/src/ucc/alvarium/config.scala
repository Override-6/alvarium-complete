package ucc.alvarium

import com.alvarium.SdkInfo
import com.alvarium.annotators.AnnotatorConfig
import com.alvarium.contracts.{AnnotationType, LayerType}
import com.alvarium.hash.{HashInfo, HashType}
import com.alvarium.sign.{KeyInfo, SignType, SignatureInfo}
import com.alvarium.streams.{MqttConfig, StreamInfo, StreamType}
import com.alvarium.utils.ServiceInfo

import java.util.UUID

def config: SdkInfo = new SdkInfo(
  Array(
    new AnnotatorConfig(
      AnnotationType.TPM,
    ),
    new AnnotatorConfig(
      AnnotationType.PKI,
    ),
    new AnnotatorConfig(
      AnnotationType.SourceCode,
    ),
    new AnnotatorConfig(
      AnnotationType.CHECKSUM,
    ),
    new AnnotatorConfig(
      AnnotationType.SOURCE,
    ),
  ),
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

  LayerType.Application
)