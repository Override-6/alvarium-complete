package ucc.alvarium

import com.alvarium.engine.AlvariumActionKind.Publish
import com.alvarium.checker.builtin.{BuiltinChecks, TestChecker}
import com.alvarium.config.SerializerType.Jsoniter
import com.alvarium.config.{Endpoint, SerializerType, SigningType, StreamType}
import com.alvarium.engine.AlvariumEngineBuilder
import com.alvarium.signing.SigningKey.FileKey
import com.alvarium.utils.{bytesToHex, hexToBytes}
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.subtle.Ed25519Sign
import zio.json.JsonEncoder

import java.nio.file.Path
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.{Executor, Executors}
import scala.concurrent.{Await, ExecutionContext, Future}


val ImagesDir = os.pwd / "data" / "images"

val Address = "alvarium-workers"
val NumberPattern = "([0-9]+)".r


@main def bench = {
  val privateKey = os.read(os.pwd / "res" / "private.key")

  given PublicKeySign = new Ed25519Sign(hexToBytes(privateKey).take(32))

  runBench(5000, 20000, 0)
  runBench(5000, 20000, 1)
  runBench(5000, 20000, 3)
  runBench(5000, 20000, 5)
  runBench(5000, 20000, 7)
  runBench(5000, 20000, 10)
  println("---")

  runBench(5000, 0, 5)
  runBench(5000, 1000, 5)
  runBench(5000, 5000, 5)
  runBench(5000, 10000, 5)
  runBench(5000, 20000, 5)
  runBench(5000, 80000, 5)

  println("END")
}

extension [A](that: Seq[A])
  def *(n: Int): Seq[A] = (1 to n).flatMap(_ => that)

val executor = Executors.newVirtualThreadPerTaskExecutor()
given ec : ExecutionContext = ExecutionContext.fromExecutor(executor)

def getEngine(annotationCount: Int) = new AlvariumEngineBuilder {
  override val signer: SigningType = SigningType.Ed25519(FileKey(Path.of("./res/private.key")), true)
  override val stream: StreamType = StreamType.Mqtt(Endpoint("mosquitto-server", "tcp", 1883), UUID.randomUUID().toString, 0, false, None)("alvarium-topic")
  override val executor: Executor = Executors.newVirtualThreadPerTaskExecutor()
  override val serializer: SerializerType = Jsoniter()

  for (i <- 0 to annotationCount) {
    addCheck(BuiltinChecks.Test(), new TestChecker())
  }
}.build()

def runBench(iterationCount: Int, dataSize: Int, annotationCount: Int)(using signer: PublicKeySign): Unit = {
  val request = provideRequest("0", new Array(dataSize), signer)
  val json = JsonEncoder[ImageRequest].encodeJson(request)
  val bytes = json.toString.getBytes()

  val engine = getEngine(annotationCount)

  measure(s"iterations : $iterationCount, bytes: $dataSize, annotations count : $annotationCount", 10) {
    val futures = for (i <- 0 to iterationCount) yield {
      //      if (i % 1000 == 0)
      //        println(i)
      engine.annotate(Publish(), bytes)
        .send()
    }
    Await.result(Future.sequence(futures), scala.concurrent.duration.Duration.Inf)
  }
}

def measure(label: String, count: Int)(f: => Unit): Unit = {
  val tries = for (i <- 1 to count) yield {
    System.gc()
    val t0 = Instant.now()
    f
    val t1 = Instant.now()
    val duration = Duration.between(t0, t1)
    duration.toMillis
  }
  val min = Duration.ofMillis(tries.min)
  val max = Duration.ofMillis(tries.max)
  val avg = Duration.ofMillis(tries.sum / tries.size)
  println(s"$label - min: $min avg: $avg max: $max")
}

def provideRequest(id: String, fileContent: Array[Byte], signer: PublicKeySign) = {
  val content = bytesToHex(fileContent)
  val seed = content.hashCode.toString

  val signature = signer.sign(seed.getBytes)
  ImageRequest(seed, bytesToHex(signature), 5, id, "label", content)
}


