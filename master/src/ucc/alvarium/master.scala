package ucc.alvarium

import zio.*


object master extends ZIOAppDefault {
  def run = {
    val lines = os.read
      .lines
      .stream(os.pwd / "data" / "styles.csv")
      .take(10000)
      .toSeq

    val workflow = for {
      f <- mqttPipeline.forkDaemon
      _ <- streamPipeline(lines).either <* Console.printLine("Done.")
      exit <- f.await
      v <- exit.either
      _ <- Console.printLine(s"MQTT Ended : $v")
      _ <- ZIO.never
//      _ <-  ZIO.sleep(60.seconds) &> Console.printLine("Waiting 60 seconds before mqtt client termination.")
    } yield ()

    workflow
  }
}

