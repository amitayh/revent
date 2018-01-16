package org.revent.cqrs

import java.time.{Clock, Instant, ZoneOffset}

import cats.instances.try_._
import org.revent._
import org.revent.testkit.text._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.util.Try

class EventSourcedCommandHandlerSpec extends Specification with Mockito {

  "Event sourced command handler" should {
    trait Context extends Scope {
      val now = Instant.now()
      val clock = Clock.fixed(now, ZoneOffset.UTC)
      val eventStore = spy(new InMemoryEventStore[SentenceStream](clock))
      val repository = new ReplayingAggregateRepository[Try, SentenceProtocol](eventStore, SentenceReducer)

      val handler = new EventSourcedCommandHandler[Try, SentenceProtocol](repository, eventStore, SentenceReducer)
    }

    "persist events produced from applying a command on an aggregate" in new Context {
      val aggregateId = 1
      eventStore.persist(aggregateId, WordAdded("foo") :: Nil)

      val word = "bar"
      val event = WordAdded(word)
      val command = AddWord(word)
      val expectedVersion: Option[Version] = Some(1)

      handler(EventSourcedCommand[Try, SentenceProtocol](aggregateId, command, expectedVersion)) must
        beSuccessfulTry(
          CommandHandled[SentenceProtocol](
            oldAggregate = AggregateSnapshot("foo" :: Nil, 1, Some(now)),
            newAggregate = AggregateSnapshot("foo" :: "bar" :: Nil, 2, Some(now)),
            persistedEvents = Event[SentenceStream](aggregateId, 2, event, now) :: Nil))

      there was one(eventStore).persist(aggregateId, event :: Nil, expectedVersion)
    }
  }

}
