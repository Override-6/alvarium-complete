package ucc.alvarium

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.jdbczio.Quill
import io.getquill.{SnakeCase, jdbczio, query, *}
import org.eclipse.paho.client.mqttv3.{IMqttMessageListener, MqttClient, MqttConnectOptions, MqttMessage}
import zio.*
import zio.json.{DeriveJsonDecoder, JsonDecoder, JsonStreamDelimiter}
import zio.metrics.Metric
import zio.stream.ZStream

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{Base64, TimeZone}


case class AlvariumActionDTO(action: String, messageType: String, content: String)

case class AlvariumAnnotationDTOSet(items: Array[AlvariumAnnotationDTO])

case class AlvariumAnnotationDTO(id: String, key: String, hash: String, host: String, tag: String, layer: String, kind: String, signature: String, isSatisfied: Boolean, timestamp: String)

given JsonDecoder[AlvariumActionDTO] = DeriveJsonDecoder.gen[AlvariumActionDTO]
given JsonDecoder[AlvariumAnnotationDTOSet] = DeriveJsonDecoder.gen[AlvariumAnnotationDTOSet]
given JsonDecoder[AlvariumAnnotationDTO] = DeriveJsonDecoder.gen[AlvariumAnnotationDTO]

val dateFormat = {
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(TimeZone.getTimeZone("UTC"))
  df
}

extension (c: AlvariumAnnotationDTO) {
  def toAnnotation(action: String): AlvariumAnnotation = AlvariumAnnotation(action, c.id, c.key, c.hash, c.host, c.tag, c.layer, c.kind, c.signature, c.isSatisfied, new Timestamp(dateFormat.parse(c.timestamp).getTime))
}

case class AlvariumAnnotation(actionType: String, id: String, key: String, hash: String, host: String, tag: String, layer: String, kind: String, signature: String, isSatisfied: Boolean, timestamp: Timestamp)

class DatabaseService(quill: jdbczio.Quill.Postgres[SnakeCase]) {

  import quill.*

  def addAnnotation(annotation: AlvariumAnnotation) = run(query[AlvariumAnnotation].insertValue(lift(annotation)))
    .catchAllCause(ZIO.logErrorCause(_))

  def addAnnotations(annotations: Iterable[AlvariumAnnotation]) = run(liftQuery(annotations).foreach(a => query[AlvariumAnnotation].insertValue(a)))
    .catchAllCause(ZIO.logErrorCause(_))
}

object DatabaseService {
  def addAnnotation(annotation: AlvariumAnnotation) = ZIO.serviceWithZIO[DatabaseService](_.addAnnotation(annotation))
  def addAnnotations(annotations: Iterable[AlvariumAnnotation]) = ZIO.serviceWithZIO[DatabaseService](_.addAnnotations(annotations))


  val layer = ZLayer.fromFunction(new DatabaseService(_))
}

def mqttPipeline = {

  val client = new MqttClient("tcp://mosquitto-server:1883", "mqtt-storage-client")
  val options = new MqttConnectOptions()
  options.setAutomaticReconnect(true)
  options.setCleanSession(true)
  options.setConnectionTimeout(5)

  client.connect(options)

  //  val testPipeline = for {
  //    _ <- Console.printLine("Testing...")
  //    _ <- DatabaseService.addAnnotation(AlvariumAnnotation("a", "b", "c", "d", "e", "f", "g", "h", "i", true, Timestamp.from(Instant.now())))
  //    _ <- Console.printLine("Testing done.")
  //  } yield ()

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
          _ <- Console.printLine(s"publishing ($publishCount)")
        } yield ()
      }): IMqttMessageListener)
    }


    stream = for {
      msg <- ZStream.fromHub(hub)

      action <- ZStream.fromIterable(msg.getPayload).map(_.toChar) >>> JsonDecoder[AlvariumActionDTO].decodeJsonPipeline(JsonStreamDelimiter.Newline)

      annotationsJson = Base64.getDecoder.decode(action.content)

      annotations <- ZStream.fromIterable(annotationsJson).map(_.toChar) >>> JsonDecoder[AlvariumAnnotationDTOSet].decodeJsonPipeline(JsonStreamDelimiter.Newline)
      annotation <- ZStream.fromIterable(annotations.items)
    } yield annotation.toAnnotation(action.action)
    _ <- stream
      .timeout(7.seconds)
      .groupedWithin(250, 3.seconds)
      .mapZIOParUnordered(TCount) { annotations =>
      for {
        counter <- annotationCounter.value
        publishCounter <- publishCounter.value
        _ <- Console.printLine(s"Inserting annotation inside database (count = ${counter.count})...")
        _ <- DatabaseService.addAnnotations(annotations) @@ annotationCounter
      } yield ()
    }.runDrain
  } yield ()

  pipeline
    .catchAllCause(ZIO.logErrorCause(_))
    .provide(
      DatabaseService.layer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      Quill.DataSource.fromDataSource(new HikariDataSource(new HikariConfig("config/database.properties")))
    )

}