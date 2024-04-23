package ucc.alvarium

import zio.json.*

case class ImageRequest(seed: String, signature: String, remainingHopCount: Int, id: String, imageB64: String)

given JsonEncoder[ImageRequest] = DeriveJsonEncoder.gen[ImageRequest]
given JsonDecoder[ImageRequest] = DeriveJsonDecoder.gen[ImageRequest]
