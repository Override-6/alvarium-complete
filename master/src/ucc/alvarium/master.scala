package ucc.alvarium

import zio._


object master extends ZIOAppDefault {
  def run = {


    val lines = os.read.lines(os.pwd / "data" / "styles.csv")

    for {
      f <- mqttPipeline.fork
      _ <- streamPipeline(lines) <* Console.printLine("Done.")
      _ <- ZIO.sleep(5.seconds) *> Console.printLine("Waiting 5 seconds before mqtt client termination.")
      _ <- f.interrupt
    } yield ()
  }
}

