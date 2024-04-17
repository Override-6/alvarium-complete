import zio.*

object sandbox extends ZIOAppDefault {


  def run = ZIO.attempt("")
    .map(str => throw Exception())
    .catchAll(e => ZIO.logError(e.toString))


}