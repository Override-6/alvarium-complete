package ucc.alvarium

import zio.*


object emitter extends ZIOAppDefault {

  java.lang.System.setProperty("java.net.preferIPv4Stack", "true")

  def run = {
    val workflow = for {
      args <- ZIO.service[ZIOAppArgs].map(_.getArgs)
      untrusted = args.contains("untrusted")
      _ <- Console.printLine(s"arguments $args, untrusted = $untrusted")
      lines <- ZIO.attempt {
        os.read
          .lines
          .stream(os.pwd / "data" / "styles.csv")
          //          .take(10000)
          .toSeq
      }
      (trustedPart, untrustedPart) = lines.splitAt(lines.size / 2)
      _ <- streamPipeline(if untrusted then untrustedPart else trustedPart, untrusted).either <* Console.printLine("Done.")
      _ <- ZIO.never
      //      _ <-  ZIO.sleep(60.seconds) &> Console.printLine("Waiting 60 seconds before mqtt client termination.")
    } yield ()

    workflow
  }
}
