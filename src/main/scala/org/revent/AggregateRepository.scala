package org.revent

import cats.implicits._
import org.revent.Reducer.AggregateReducer
import org.revent.ReplayingAggregateRepository._

import scala.language.higherKinds

trait AggregateRepository[F[_], P <: Protocol] {
  type Snapshot = AggregateSnapshot[P#Aggregate]

  def load(aggregateId: P#EventStreamId,
           expectedVersion: Option[Version] = None): F[Snapshot]
}

class ReplayingAggregateRepository[F[_], P <: Protocol]
  (eventStream: EventStreamReader[F, P#EventStream],
   reducer: AggregateReducer[P],
   pageSize: Int = DefaultPageSize)
  (implicit F: MonadThrowable[F]) extends AggregateRepository[F, P] {

  private val snapshotReducer = new SnapshotReducer[P](reducer)

  override def load(aggregateId: P#EventStreamId,
                    expectedVersion: Option[Version]): F[Snapshot] = {
    reconstituteFromEvents(aggregateId).flatMap { snapshot =>
      if (snapshot.conformsTo(expectedVersion)) F.pure(snapshot)
      else F.raiseError(new VersionMismatch(aggregateId, expectedVersion, snapshot.version))
    }
  }

  private def reconstituteFromEvents(streamId: P#EventStreamId): F[Snapshot] = {
    F.tailRecM(snapshotReducer.empty) { snapshot =>
      val nextVersion = snapshot.version.nextVersion
      eventStream.read(streamId, nextVersion, pageSize).map { events =>
        val consumedAllEvents = events.size < pageSize
        val updatedSnapshot = events.foldLeft(snapshot)(snapshotReducer.handle)
        if (consumedAllEvents) Right(updatedSnapshot) else Left(updatedSnapshot)
      }
    }
  }

}

object ReplayingAggregateRepository {
  val DefaultPageSize = 512
}

class VersionMismatch[AggregateId](aggregateId: AggregateId, expected: Option[Version], actual: Version)
  extends RuntimeException(s"Aggregate $aggregateId expected to be at version $expected, actually at version $actual")
