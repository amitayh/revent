package org.revent.eventstore

import java.time.{Clock, Instant}
import java.util.ConcurrentModificationException

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import eventstore.j.{EventDataBuilder, WriteEventsBuilder}
import eventstore.{EventData, EventNumber, WriteEventsCompleted, WrongExpectedVersionException}
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import org.revent.eventstore.EventStoreEventStreamWriter._
import org.revent.{Event, EventStream, EventStreamWriter, Version}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class EventStoreEventStreamWriter[ES <: EventStream]
  (connection: ActorRef,
   encoder: Encoder[ES#Payload],
   config: EventStoreConfig,
   clock: Clock = Clock.systemUTC())
  (implicit executionContext: ExecutionContext = ExecutionContext.global)
  extends EventStreamWriter[Future, ES] {

  implicit val timeout = Timeout(config.timeout)

  override def persist(streamId: ES#Id,
                       events: Seq[ES#Payload],
                       expectedVersion: Option[Version]): Future[Seq[Event[ES]]] = {
    if (events.isEmpty) Future.successful(Nil)
    else appendNonEmpty(streamId, events, expectedVersion)
  }

  private def appendNonEmpty(streamId: ES#Id,
                             events: Seq[ES#Payload],
                             expectedVersion: Option[Version]): Future[Seq[Event[ES]]] = {
    val writeEvents = writeEventsBuilder(streamId, events, expectedVersion)
    val result = connection ? writeEvents.build
    result
      .mapTo[WriteEventsCompleted]
      .transform(
        getPersistedEvents(streamId, events),
        transformException)
  }

  private def writeEventsBuilder(streamId: ES#Id,
                                 events: Seq[ES#Payload],
                                 expectedVersion: Option[Version]) = {
    val now = clock.instant()
    val eventsData = events.map(toEventData(now)).asJava
    val builder = new WriteEventsBuilder(streamId.toString).addEvents(eventsData)
    expectedVersion match {
      case Some(version) if version.isFirst => builder.expectNoStream
      case Some(version) => builder.expectVersion(version - 1)
      case _ => builder.expectAnyVersion
    }
  }

  private def toEventData(now: Instant)(event: ES#Payload): EventData = {
    val metaData = EventMetaData(now).asJson.noSpaces
    new EventDataBuilder(event.getClass.getSimpleName)
      .data(encoder(event).noSpaces)
      .metadata(metaData)
      .build
  }

  private def getPersistedEvents(streamId: ES#Id, events: Seq[ES#Payload])
                                (result: WriteEventsCompleted): Seq[Event[ES]] = {
    val now = clock.instant()
    val range = result.numbersRange.get
    val versions = range.versions
    (events zip versions).map {
      case (event, version) => Event(streamId, version, event, now)
    }
  }

  private def transformException(e: Throwable): Throwable = e match {
    case _: WrongExpectedVersionException => new ConcurrentModificationException(e)
    case _ => e
  }

}

object EventStoreEventStreamWriter {
  implicit class EventRangeOps(range: EventNumber.Range) {
    def versions: Range = (range.start.value + 1) to (range.end.value + 1)
  }
}
