package org.revent

import org.revent.AggregateSnapshot.InitialVersion
import org.revent.Reducer.AggregateReducer

trait Reducer[State, Event] {
  def empty: State
  def handle(state: State, event: Event): State
}

object Reducer {
  type AggregateReducer[P <: Protocol] = Reducer[P#Aggregate, P#EventPayload]
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
