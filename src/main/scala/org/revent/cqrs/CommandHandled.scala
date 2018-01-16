package org.revent.cqrs

import org.revent.{AggregateSnapshot, Event, Protocol}

import scala.collection.immutable.Seq

case class CommandHandled[P <: Protocol]
  (oldAggregate: AggregateSnapshot[P#Aggregate],
   newAggregate: AggregateSnapshot[P#Aggregate],
   persistedEvents: Seq[Event[P#EventStream]])
