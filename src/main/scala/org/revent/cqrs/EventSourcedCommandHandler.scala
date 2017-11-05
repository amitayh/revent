package org.revent.cqrs

import cats.FlatMap
import cats.implicits._
import org.revent.Reducer.AggregateReducer
import org.revent._

import scala.language.higherKinds

class EventSourcedCommandHandler[F[_]: FlatMap, P <: Protocol]
  (repository: AggregateRepository[F, P],
   eventStream: EventStreamWriter[F, P#EventStream],
   reducer: AggregateReducer[P])
  extends (EventSourcedCommand[F, P] => F[CommandHandled[P]]) {

  override def apply(command: EventSourcedCommand[F, P]): F[CommandHandled[P]] = for {
    snapshot <- repository.load(command.aggregateId, command.expectedVersion)
    commandEvents <- command.toEvents(snapshot.aggregate)
    persistedEvents <- eventStream.persist(command.aggregateId, commandEvents, Some(snapshot.version))
    newAggregate = commandEvents.foldLeft(snapshot.aggregate)(reducer.handle)
  } yield CommandHandled(snapshot.aggregate, newAggregate, persistedEvents)

}
