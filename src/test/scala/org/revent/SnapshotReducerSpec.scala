package org.revent

import java.time.Instant

import org.revent.testkit.text.{SentenceProtocol, SentenceReducer, SentenceStream, WordAdded}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SnapshotReducerSpec extends Specification {

  "Snapshot reducer" should {
    trait Context extends Scope {
      val snapshotReducer = new SnapshotReducer[SentenceProtocol](SentenceReducer)

      val aggregateId = 1
      val now = Instant.now()
    }

    "reduce events using aggregate reducer" in new Context {
      val events =
        Event[SentenceStream](aggregateId, 1, WordAdded("foo"), now) ::
        Event[SentenceStream](aggregateId, 2, WordAdded("bar"), now) :: Nil

      events.foldLeft(snapshotReducer.empty)(snapshotReducer.handle) must
        equalTo(AggregateSnapshot("foo" :: "bar" :: Nil, 2, Some(now)))
    }
  }

}
