package ucc.alvarium

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill._
import io.getquill.jdbczio.Quill
import io.getquill.{SnakeCase, query, quote}
import org.eclipse.paho.client.mqttv3.{IMqttMessageListener, MqttClient, MqttConnectOptions, MqttMessage}
import org.postgresql.ds.PGSimpleDataSource
import zio._
import zio.json.{DeriveJsonDecoder, JsonDecoder, JsonStreamDelimiter}
import zio.stream.ZStream

import java.util.Base64
import javax.sql.DataSource


case class AlvariumActionDTO(action: String, messageType: String, content: String)

case class AlvariumAnnotationDTOSet(items: Array[AlvariumAnnotationDTO])

case class AlvariumAnnotationDTO(id: String, key: String, hash: String, host: String, tag: String, layer: String, kind: String, signature: String, isSatisfied: Boolean, timestamp: String)

given JsonDecoder[AlvariumActionDTO] = DeriveJsonDecoder.gen[AlvariumActionDTO]
given JsonDecoder[AlvariumAnnotationDTOSet] = DeriveJsonDecoder.gen[AlvariumAnnotationDTOSet]
given JsonDecoder[AlvariumAnnotationDTO] = DeriveJsonDecoder.gen[AlvariumAnnotationDTO]

case class AlvariumAnnotation(actionType: String, id: String, key: String, hash: String, host: String, tag: String, layer: String, kind: String, signature: String, isSatisfied: Boolean, timestamp: String)

class DatabaseService(quill: Quill.Postgres[SnakeCase]) {

  import quill._

  def addAnnotation(annotation: AlvariumAnnotation) = run(query[AlvariumAnnotation].insertValue(lift(annotation)))
    .catchAllCause(ZIO.logErrorCause(_))
}

object DatabaseService {
  def addAnnotation(annotation: AlvariumAnnotation) = ZIO.serviceWithZIO[DatabaseService](_.addAnnotation(annotation))


  val layer = ZLayer.fromFunction(new DatabaseService(_))
}

def mqttPipeline = {

  val client = new MqttClient("tcp://localhost:1883", "mqtt-storage-client")
  val options = new MqttConnectOptions()
  options.setAutomaticReconnect(true)
  options.setCleanSession(true)
  options.setConnectionTimeout(5)

  client.connect(options)

  val pipeline = (for {
    hub <- Hub.unbounded[MqttMessage]
    r <- ZIO.runtime
    _ <- ZIO.attempt {
      client.subscribe("alvarium-topic", ((_, msg) => r.unsafe.run {
        hub.publish(msg)
      }): IMqttMessageListener)
    }

    _ <- (for {
      msg <- ZStream.fromHub(hub)
      action <- ZStream.fromIterable(msg.getPayload).map(_.toChar) >>> JsonDecoder[AlvariumActionDTO].decodeJsonPipeline(JsonStreamDelimiter.Newline)

      annotationsJson = Base64.getDecoder.decode(action.content)

      annotations <- (ZStream.fromIterable(annotationsJson).map(_.toChar) >>> JsonDecoder[AlvariumAnnotationDTOSet].decodeJsonPipeline(JsonStreamDelimiter.Newline))
      .tap(a => Console.printLine("Inserting annotations inside database...") *> ZIO.foreach(a.items)(a => DatabaseService.addAnnotation(AlvariumAnnotation(action.action, a.id, a.key, a.hash, a.host, a.tag, a.layer, a.kind, a.signature, a.isSatisfied, a.timestamp))))
    } yield ())
      .runDrain

  } yield ())
    .catchAllCause(ZIO.logErrorCause(_))

  pipeline
    .provide(
      DatabaseService.layer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      Quill.DataSource.fromDataSource(new HikariDataSource(new HikariConfig("database.properties")))
    )
}