package org.revent

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

import cats.implicits._
import org.specs2.matcher.{Matcher, Matchers}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.collection.immutable.Seq
import scala.compat.Platform.ConcurrentModificationException
import scala.language.higherKinds
import scala.reflect.ClassTag

trait EventStoreContract[F[_]] extends Specification {

  sequential

  val now = Instant.now()

  val clock = Clock.fixed(now, ZoneOffset.UTC)

  def createReader: EventStreamReader[F, ExampleStream]

  def createWriter: EventStreamWriter[F, ExampleStream]

  val matchers: EventStoreMatchers[F]

  implicit val monadInstance: MonadThrowable[F]

  import matchers.{fail, failWith, succeed, succeedWith}

  implicit class ChainingOps[T](a: F[T]) {
    def andThen(b: => F[T]): F[T] = a.attempt.flatMap(_ => b)
  }

  "Event store" should {
    trait Context extends Scope {
      val reader = createReader
      val writer = createWriter

      val streamId = UUID.randomUUID()
      val otherStreamId = UUID.randomUUID()
      val eventPayload1 = ExampleEvent("foo")
      val eventPayload2 = ExampleEvent("bar")
      val eventPayload3 = ExampleEvent("baz")
      val eventPayload4 = ExampleEvent("qux")

      def beAtTheSameTimeOrAfter(other: Instant): Matcher[Instant] = {
        beGreaterThanOrEqualTo(0) ^^ { (_: Instant) compareTo other }
      }

      def eventWithVersion(version: Version): Matcher[Event[ExampleStream]] = {
        equalTo(version) ^^ { (_: Event[ExampleStream]).version }
      }

      def eventWith(version: Version, payload: ExampleEvent): Matcher[Event[ExampleStream]] = {
        eventWithVersion(version) and
        equalTo(streamId) ^^ { (_: Event[ExampleStream]).streamId } and
        equalTo(payload) ^^ { (_: Event[ExampleStream]).payload }
        beAtTheSameTimeOrAfter(now) ^^ { (_: Event[ExampleStream]).timestamp }
      }

      val event1 = eventWith(version = 1, payload = eventPayload1)
      val event2 = eventWith(version = 2, payload = eventPayload2)
      val event3 = eventWith(version = 3, payload = eventPayload3)
      val event4 = eventWith(version = 4, payload = eventPayload4)
    }

    "return empty stream if no events were saved" in new Context {
      reader.read(streamId, 1, 100) must succeedWith(beEmpty)
    }

    "persist zero events" in new Context {
      writer.persist(streamId, Nil) must succeedWith(beEmpty)
    }

    "persist one event" in new Context {
      writer.persist(streamId, eventPayload1 :: Nil) must
        succeedWith(contain(event1))
    }

    "persist event for correct stream ID" in new Context {
      val result =
        writer.persist(streamId, eventPayload1 :: Nil) andThen
          reader.read(otherStreamId, 1, 100)

      result must succeedWith(beEmpty)
    }

    "persist multiple events" in new Context {
      writer.persist(streamId, eventPayload1 :: eventPayload2 :: Nil) must
        succeedWith(contain(allOf(event1, event2).inOrder))
    }

    "keep old events" in new Context {
      val result =
        writer.persist(streamId, eventPayload1 :: Nil) andThen
          writer.persist(streamId, eventPayload2 :: Nil) andThen
          reader.read(streamId, 1, 100)

      result must succeedWith(contain(allOf(event1, event2).inOrder))
    }

    "fetch persisted events" in new Context {
      val result =
        writer.persist(streamId, eventPayload1 :: eventPayload2 :: Nil) andThen
          reader.read(streamId, 1, 100)

      result must succeedWith(contain(allOf(event1, event2).inOrder))
    }

    "persist events with expected version" in new Context {
      val persist1 = writer.persist(streamId, eventPayload1 :: Nil, Some(0))
      persist1 must succeedWith(contain(eventWithVersion(1)))

      val persist2 = persist1 andThen writer.persist(streamId, eventPayload2 :: Nil, Some(1))
      persist2 must succeedWith(contain(eventWithVersion(2)))
    }

    "fail if expected version doesn't match" in new Context {
      val result =
        writer.persist(streamId, eventPayload1 :: Nil, Some(0)) andThen
          writer.persist(streamId, eventPayload2 :: Nil, Some(0))

      result must failWith[ConcurrentModificationException]
    }

    "not allow version gaps" >> {
      "for first event" in new Context {
        writer.persist(streamId, eventPayload1 :: Nil, Some(1)) must fail
      }

      "for subsequent events" in new Context {
        val result =
          writer.persist(streamId, eventPayload1 :: Nil, Some(0)) andThen
            writer.persist(streamId, eventPayload2 :: Nil, Some(2))

        result must fail
      }
    }

    "persist events atomically" in new Context {
      val persist1 = writer.persist(streamId, eventPayload1 :: Nil, Some(0))
      persist1 must succeed

      val persist2 = persist1 andThen writer.persist(streamId, eventPayload2 :: eventPayload3 :: Nil, Some(0))
      persist2 must fail

      val read = persist2 andThen reader.read(streamId, 1, 100)
      read must succeedWith(contain(exactly(event1)))
    }

    "fetch events from correct position" in new Context {
      val events = eventPayload1 :: eventPayload2 :: eventPayload3 :: eventPayload4 :: Nil
      val persist = writer.persist(streamId, events, Some(0))

      val read1 = persist andThen reader.read(streamId, 1, 3)
      read1 must succeedWith(contain(exactly(event1, event2, event3)))

      val read2 = persist andThen reader.read(streamId, 4, 3)
      read2 must succeedWith(contain(exactly(event4)))
    }

    "allow fetching from beginning of stream with version 0 (same as from version 1)" in new Context {
      val result =
        writer.persist(streamId, eventPayload1 :: eventPayload2 :: Nil) andThen
          reader.read(streamId, 0, 1)

      result must succeedWith(contain(exactly(event1)))
    }
  }

}

case class ExampleEvent(name: String)

trait ExampleStream extends EventStream {
  override type Id = UUID
  override type Payload = ExampleEvent
}

trait EventStoreMatchers[F[_]] extends Matchers {
  type Result = Seq[Event[ExampleStream]]
  def succeed: Matcher[F[Result]] = not(fail)
  def succeedWith(value: Matcher[Result]): Matcher[F[Result]]
  def fail: Matcher[F[Result]]
  def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[F[Result]]
}
