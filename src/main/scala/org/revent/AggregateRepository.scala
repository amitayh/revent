package org.revent

import cats.implicits._
import org.revent.AggregateSnapshot.InitialVersion
import org.revent.Reducer.AggregateReducer

import scala.collection.immutable.Seq
import scala.language.higherKinds

trait AggregateRepository[F[_], P <: Protocol] {
  def load(aggregateId: P#EventStreamId,
           expectedVersion: Option[Int] = None): F[AggregateSnapshot[P#Aggregate]]
}

class ReplayingAggregateRepository[F[_], P <: Protocol]
  (eventStream: EventStreamReader[F, P#EventStream],
   reducer: AggregateReducer[P],
   pageSize: Int = ReplayingAggregateRepository.DefaultPageSize)
  (implicit M: MonadThrowable[F]) extends AggregateRepository[F, P] {

  type Events = Seq[Event[P#EventStream]]
  type Snapshot = AggregateSnapshot[P#Aggregate]

  private val snapshotReducer = new SnapshotReducer[P](reducer)

  override def load(aggregateId: P#EventStreamId,
                    expectedVersion: Option[Int]): F[Snapshot] = {
    snapshotFor(aggregateId) flatMap { snapshot =>
      if (snapshot.conformsTo(expectedVersion)) M.pure(snapshot)
      else M.raiseError(new VersionMismatch(aggregateId, expectedVersion, snapshot.version))
    }
  }

  private def snapshotFor(streamId: P#EventStreamId): F[Snapshot] = {
    def go(page: F[Events], snapshot: Snapshot): F[Snapshot] = page flatMap { events =>
      val updatedSnapshot = events.foldLeft(snapshot)(snapshotReducer.handle)
      if (events.size < pageSize) M.pure(updatedSnapshot)
      else go(nextPage(streamId, events), updatedSnapshot)
    }

    go(firstPage(streamId), snapshotReducer.empty)
  }

  private def firstPage(streamId: P#EventStreamId): F[Events] =
    eventStream.read(streamId, InitialVersion, pageSize)

  private def nextPage(streamId: P#EventStreamId, events: Events): F[Events] = {
    val lastVersion = events.last.version
    val nextVersion = lastVersion + 1
    eventStream.read(streamId, nextVersion, pageSize)
  }

}

object ReplayingAggregateRepository {
  val DefaultPageSize = 512
}

class VersionMismatch[AggregateId](aggregateId: AggregateId, expected: Option[Int], actual: Int)
  extends RuntimeException(s"Aggregate $aggregateId expected to be at version $expected, actually at version $actual")
