package org.revent.cqrs

import org.revent.{Protocol, Version}

import scala.collection.immutable.Seq
import scala.language.higherKinds

case class EventSourcedCommand[F[_], P <: Protocol]
  (aggregateId: P#EventStreamId,
   toEvents: P#Aggregate => F[Seq[P#EventPayload]],
   expectedVersion: Option[Version] = None)
