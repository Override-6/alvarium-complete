package ucc.alvarium

import com.alvarium.hash.HashProviderFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*
import zio.http.*
import zio.json.JsonDecoder
import zio.metrics.Metric

object storage extends ZIOAppDefault {

  val app = Routes(
    Method.GET / "storage" -> handler(storeImageInfo(_)),
  ).toHttpApp

  java.lang.System.setProperty("java.net.preferIPv4Stack", "true")
  
  def run = for {
    _ <- mqttPipeline.fork
    _ <- Server
      .serve(app)
      .provide(
        Server.default,
        DataInfoDatabaseService.layer,
        Quill.Postgres.fromNamingStrategy(SnakeCase),
        Quill.DataSource.fromDataSource(new HikariDataSource(new HikariConfig("config/database.properties")))
      )
    _ <- ZIO.never
  } yield ()
}

case class DataInfo(imageHash: String, label: String)

class DataInfoDatabaseService(quill: Quill.Postgres[SnakeCase]) {

  import quill.*

  def insertDataInfo(annotation: DataInfo) = run(query[DataInfo].insertValue(lift(annotation)))
    .catchAllCause(ZIO.logErrorCause(_))
}

object DataInfoDatabaseService {
  val layer = ZLayer.fromFunction(new DataInfoDatabaseService(_))
}


val hasher = new HashProviderFactory().getProvider(config.getHash.getType)

def storeImageInfo(request: Request) = {
  for {
    data <- request.body.asString
    db <- ZIO.service[DataInfoDatabaseService]

    _ <- (for {
      requestData <- ZIO.from(JsonDecoder[ImageRequest].decodeJson(data))
      hash = hasher.derive(requestData.imageB64.getBytes)
      _ <- db.insertDataInfo(DataInfo(hash, requestData.label))
    } yield ()).forkDaemon

  } yield Response()
}.tapErrorCause(ZIO.logErrorCause(_))
  .catchAllCause(_ => ZIO.succeed(Response()))