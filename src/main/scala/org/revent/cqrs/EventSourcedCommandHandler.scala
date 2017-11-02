package org.revent.cqrs

import cats.implicits._
import org.revent._

import scala.collection.immutable.Seq
import scala.language.higherKinds

class EventSourcedCommandHandler[F[_], P <: Protocol]
  (repository: AggregateRepository[F, P], eventStream: EventStreamWriter[F, P#EventStream])
  (implicit mi: MonadThrowable[F]) extends (EventSourcedCommand[F, P] => F[Seq[Event[P#EventStream]]]) {

  override def apply(command: EventSourcedCommand[F, P]): F[Seq[Event[P#EventStream]]] = for {
    snapshot <- repository.load(command.aggregateId, command.expectedVersion)
    commandEvents <- command.toEvents(snapshot.aggregate)
    persistedEvents <- eventStream.persist(command.aggregateId, commandEvents, Some(snapshot.version))
  } yield persistedEvents

}
