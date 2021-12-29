package com.bootcamp.streamreader

import cats.effect._
import cats.effect.kernel.Ref
import com.bootcamp.config.DbConfig
import fs2._
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import java.time.{Instant, ZoneId}

object FlushInactiveProfiles {
  def of(
    ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]],
    config: DbConfig,
  ): IO[FlushInactiveProfiles] =
    IO(new FlushInactiveProfiles(ref, config))
}

class FlushInactiveProfiles(ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]], config: DbConfig) {
  def apply: Stream[IO, Unit] =
    Stream
      .awakeEvery[IO](config.timeToLiveSeconds)
      .evalMap(_ =>
        for {
          currentState <- ref.get
          maxAge = Instant.now().atZone(ZoneId.of("UTC")).minusSeconds(config.timeToLiveSeconds.toSeconds).toEpochSecond
          inactiveProfiles = currentState.filter(i => i._2.lastUpdate < maxAge).keySet
          _ <- ref.updateAndGet { state =>
            state -- inactiveProfiles
          }
          _ <- IO.pure(println(s"Cleared inactive profiles ${inactiveProfiles.toString()}\n"))
        } yield (),
      )
}
