import zio.*
import zio.stream.ZStream

object sandbox extends ZIOAppDefault {


  def run = ZStream("Banana", "Apple", "Strawberry", "Mango")
    .tap(s => Console.printLine(s"Injection de $s - (thread ${Thread.currentThread()})"))
    .tap(s => Console.printLine(s"Injection du fruit $s dans la pipeline asynchrone - (thread ${Thread.currentThread()})"))
    .mapZIOParUnordered(4)(s => Console.printLine(s"IN PARALLELL : ${s} - (thread ${Thread.currentThread()})") *> ZIO.succeed(s))
    .tap(s => Console.printLine(s * 2 + s" - (thread ${Thread.currentThread()})"))
    .tap(s => Console.printLine(s.length + s" - (thread ${Thread.currentThread()})"))
    .foreach(s =>
      Console.printLine(s"fruit : $s" + s" - (thread ${Thread.currentThread()})")
    )


}