package org.revent

import java.time.Clock
import java.util.ConcurrentModificationException

import org.revent.AggregateSnapshot.InitialVersion
import org.revent.Event._

import scala.collection.concurrent.{TrieMap, Map => ConcurrentMap}
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

class InMemoryEventStore[ES <: EventStream](clock: Clock = Clock.systemUTC()) extends EventStore[Try, ES] {

  private val store: ConcurrentMap[ES#Id, Seq[Event[ES]]] = TrieMap.empty

  override def persist(streamId: ES#Id,
                       events: Seq[ES#Payload],
                       expectedVersion: Option[Int]): Try[Seq[Event[ES]]] = store.synchronized {
    lastVersionFor(streamId, expectedVersion).map { lastVersion =>
      val now = clock.instant()
      val nextEvents = events.toEventStream(streamId, lastVersion, now)
      store.update(streamId, read(streamId) ++ nextEvents)
      nextEvents
    }
  }

  override def read(streamId: ES#Id,
                    fromVersion: Int,
                    maxCount: Int): Try[Seq[Event[ES]]] = Try {
    val fromIndex = (fromVersion - 1) max 0
    val toIndex = fromIndex + maxCount
    read(streamId).slice(fromIndex, toIndex)
  }

  private def lastVersionFor(streamId: ES#Id, expectedVersion: Option[Int]): Try[Int] = {
    val lastEvent = read(streamId).lastOption
    val lastVersion = lastEvent.map(_.version).getOrElse(InitialVersion)
    if (expectedVersion.exists(_ != lastVersion)) Failure(new ConcurrentModificationException())
    else Success(lastVersion)
  }

  private def read(streamId: ES#Id): Seq[Event[ES]] = store.getOrElse(streamId, Vector.empty)

}
