package org.revent

import java.time.{Clock, Instant, ZoneOffset}

import cats.instances.try_._
import org.revent.testkit.text._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.util.Try

class ReplayingAggregateRepositorySpec extends Specification {

  "Replaying event sourced repository" should {
    trait Context extends Scope {
      val now = Instant.now()
      val clock = Clock.fixed(now, ZoneOffset.UTC)
      val eventStore = new InMemoryEventStore[SentenceStream](clock)
      val repository = new ReplayingAggregateRepository[Try, SentenceProtocol](eventStore, SentenceReducer, pageSize = 2)

      val aggregateId = 1

      def givenEvents(eventPayloads: SentenceEvent*): Unit = {
        eventStore.persist(aggregateId, eventPayloads.toList)
      }
    }

    "return an empty snapshot if no events exist for aggregate" in new Context {
      repository.load(aggregateId) must beSuccessfulTry(AggregateSnapshot(Nil, 0))
    }

    "use reducer to produce current aggregate snapshot from events" in new Context {
      givenEvents(WordAdded("foo"), WordAdded("bar"), WordAdded("baz"))

      repository.load(aggregateId) must
        beSuccessfulTry(AggregateSnapshot("foo" :: "bar" :: "baz" :: Nil, 3, Some(now)))
    }

    "fail if expected version is different than actual version" in new Context {
      repository.load(aggregateId, Some(1)) must beFailedTry
    }
  }

}
