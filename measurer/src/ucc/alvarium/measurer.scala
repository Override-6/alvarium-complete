package ucc.alvarium

import zio.*


object measurer extends ZIOAppDefault {
  override def run = app
}

val app = for {
  () <- Console.printLine("Hello world")
} yield ()
