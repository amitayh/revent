package org.revent

trait Reducer[State, Event] {
  def empty: State
  def handle(state: State, event: Event): State
}

object Reducer {
  type AggregateReducer[P <: Protocol] = Reducer[P#Aggregate, P#EventPayload]
}
