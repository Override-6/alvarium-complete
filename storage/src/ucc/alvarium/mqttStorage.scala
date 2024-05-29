package ucc.alvarium

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.jdbczio.Quill
import io.getquill.{SnakeCase, jdbczio, query, *}
import org.eclipse.paho.client.mqttv3.{IMqttMessageListener, MqttClient, MqttConnectOptions, MqttMessage}
import zio.*
import zio.json.{DeriveJsonDecoder, JsonDecoder, JsonStreamDelimiter}
import zio.metrics.Metric
import zio.stream.ZStream

import java.text.SimpleDateFormat
import java.util.TimeZone


case class AlvariumActionDTO(action: String, `type`: String, content: SignedAnnotationBundle)

case class SignedAnnotationBundle(signature: String, annotations: Array[AlvariumAnnotationDTO], key: String, hash: String, host: String, layer: String, timestamp: String)

case class AlvariumAnnotationDTO(id: String, tag: String, `type`: String, isSatisfied: Boolean)

case class AlvariumAnnotation(id: String, tag: String, `type`: String, isSatisfied: Boolean, host: String, timestamp: String, imageHash: String)

given JsonDecoder[AlvariumActionDTO] = DeriveJsonDecoder.gen[AlvariumActionDTO]
given JsonDecoder[SignedAnnotationBundle] = DeriveJsonDecoder.gen[SignedAnnotationBundle]
given JsonDecoder[AlvariumAnnotationDTO] = DeriveJsonDecoder.gen[AlvariumAnnotationDTO]
given JsonDecoder[AlvariumAnnotation] = DeriveJsonDecoder.gen[AlvariumAnnotation]

val dateFormat = {
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(TimeZone.getTimeZone("UTC"))
  df
}

val TCount = java.lang.Runtime.getRuntime.availableProcessors()


class AnnotationDatabaseService(quill: jdbczio.Quill.Postgres[SnakeCase]) {

  import quill.*

  def addAnnotation(annotation: AlvariumAnnotation) = run(query[AlvariumAnnotation].insertValue(lift(annotation)))
    .catchAllCause(ZIO.logErrorCause(_))

  def addAnnotations(annotations: Iterable[AlvariumAnnotation]) = run(liftQuery(annotations).foreach(a => query[AlvariumAnnotation].insertValue(a)))
    .catchAllCause(ZIO.logErrorCause(_))
}

object AnnotationDatabaseService {
  def addAnnotation(annotation: AlvariumAnnotation) = ZIO.serviceWithZIO[AnnotationDatabaseService](_.addAnnotation(annotation))

  def addAnnotations(annotations: Iterable[AlvariumAnnotation]) = ZIO.serviceWithZIO[AnnotationDatabaseService](_.addAnnotations(annotations))

  val layer = ZLayer.fromFunction(new AnnotationDatabaseService(_))
}


def mqttPipeline = {

  val client = new MqttClient("tcp://mosquitto-server:1883", "mqtt-storage-client")
  val options = new MqttConnectOptions()
  options.setAutomaticReconnect(true)
  options.setCleanSession(true)
  options.setConnectionTimeout(5)

  client.connect(options)

  val annotationCounter = Metric.counter("Annotations count").fromConst(1)
  val publishCounter = Metric.counter("publish count").fromConst(1)

  val pipeline = for {
    hub <- Hub.unbounded[MqttMessage]
    r <- ZIO.runtime
    _ <- ZIO.attempt {
      client.subscribe("alvarium-topic", ((_, msg) => r.unsafe.run {
        for {
          _ <- hub.publish(msg) @@ publishCounter
          publishCount <- publishCounter.value
        } yield ()
      }): IMqttMessageListener)
    }


    stream = for {
      msg <- ZStream.fromHub(hub)

      action <- ZStream.fromIterable(msg.getPayload).map(_.toChar) >>> JsonDecoder[AlvariumActionDTO].decodeJsonPipeline(JsonStreamDelimiter.Newline)
      bundle = action.content
      imageHash = bundle.key
      host = bundle.host
      timestamp = bundle.timestamp
      annotation <- ZStream.fromIterable(action.content.annotations)
    } yield AlvariumAnnotation(annotation.id, annotation.tag, annotation.`type`, annotation.isSatisfied, host, timestamp, imageHash)
    _ <- stream
      .groupedWithin(5000, 3.seconds)
      .mapZIOParUnordered(TCount) { annotations =>
        for {
          counter <- annotationCounter.value
          publishCounter <- publishCounter.value
          _ <- Console.printLine(s"Inserting annotation inside database (count = ${counter.count})...")
          _ <- AnnotationDatabaseService.addAnnotations(annotations) @@ annotationCounter
        } yield ()
      }.runDrain
  } yield ()

  pipeline
    .catchAllCause(ZIO.logErrorCause(_))
    .provide(
      AnnotationDatabaseService.layer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      Quill.DataSource.fromDataSource(new HikariDataSource(new HikariConfig("config/database.properties")))
    )

}