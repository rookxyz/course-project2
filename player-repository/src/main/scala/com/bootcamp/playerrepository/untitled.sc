import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntime.{
  createDefaultBlockingExecutionContext,
  createDefaultComputeThreadPool,
  createDefaultScheduler,
}
import fs2.Stream
val s = List(1, 2, 3, 4, 5).toI

val stream = Stream.eval(Option(s)).chunkN(2, true).map(println(_))
stream.compile.drain
