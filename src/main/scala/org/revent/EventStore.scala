package org.revent

import scala.collection.immutable.Seq
import scala.language.higherKinds

trait EventStore[F[_], ES <: EventStream]
  extends EventStreamReader[F, ES]
    with EventStreamWriter[F, ES]

trait EventStreamReader[F[_], ES <: EventStream] {
  def read(streamId: ES#Id,
           fromVersion: Int,
           maxCount: Int): F[Seq[Event[ES]]]
}

trait EventStreamWriter[F[_], ES <: EventStream] {
  def persist(streamId: ES#Id,
              events: Seq[ES#Payload],
              expectedVersion: Option[Int] = None): F[Seq[Event[ES]]]
}
