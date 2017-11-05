package org.revent

import java.time.Instant

import org.revent.AggregateSnapshot.InitialVersion
import org.revent.Reducer.AggregateReducer

import scala.language.higherKinds

case class AggregateSnapshot[Aggregate](aggregate: Aggregate,
                                        version: Int,
                                        timestamp: Option[Instant] = None) {

  def conformsTo(expectedVersion: Option[Int]): Boolean =
    expectedVersion.forall(_ == version)

}

object AggregateSnapshot {
  val InitialVersion = 0
}

class SnapshotReducer[P <: Protocol](aggregateReducer: AggregateReducer[P])
  extends Reducer[AggregateSnapshot[P#Aggregate], Event[P#EventStream]] {

  override val empty: AggregateSnapshot[P#Aggregate] =
    AggregateSnapshot(aggregateReducer.empty, InitialVersion)

  override def handle(snapshot: AggregateSnapshot[P#Aggregate],
                      event: Event[P#EventStream]): AggregateSnapshot[P#Aggregate] =
    AggregateSnapshot(
      aggregate = aggregateReducer.handle(snapshot.aggregate, event.payload),
      version = event.version,
      timestamp = Some(event.timestamp))

}
