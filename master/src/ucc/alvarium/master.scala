package ucc.alvarium

import zio.*


object master extends ZIOAppDefault {
  def run = {
        val lines = os.read
          .lines
          .stream(os.pwd / "data" / "styles.csv")
          .take(3)
          .toSeq

        for {
          f <- mqttPipeline.fork
          _ <- streamPipeline(lines) <* Console.printLine("Done.")
          _ <- ZIO.never// ZIO.sleep(5.seconds) &> Console.printLine("Waiting 5 seconds before mqtt client termination.")
          _ <- f.interrupt
        } yield ()
  }
}

