package org.revent.cqrs

import org.revent.{Event, Protocol}

import scala.collection.immutable.Seq

case class CommandHandled[P <: Protocol]
  (oldAggregate: P#Aggregate,
   newAggregate: P#Aggregate,
   persistedEvents: Seq[Event[P#EventStream]])
