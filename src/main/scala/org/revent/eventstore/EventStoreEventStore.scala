package org.revent.eventstore

import java.nio.ByteBuffer
import java.time.Clock
import java.util.ConcurrentModificationException

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import eventstore.j.{EventDataBuilder, ReadStreamEventsBuilder, WriteEventsBuilder}
import eventstore.{Content, EventData, EventNumber, ReadStreamEventsCompleted, StreamNotFoundException, WriteEventsCompleted, WrongExpectedVersionException}
import io.circe.generic.auto._
import io.circe.jawn.parseByteBuffer
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.revent.AggregateSnapshot.InitialVersion
import org.revent.eventstore.EventStoreEventStore._
import org.revent.{Event, EventStore, EventStream}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * Silly name because Greg Young chose such a generic name for his product :(
  */
class EventStoreEventStore[ES <: EventStream]
  (connection: ActorRef,
   encoder: Encoder[ES#Payload],
   decoder: Decoder[ES#Payload],
   config: EventStoreConfig,
   clock: Clock = Clock.systemUTC())
  (implicit executionContext: ExecutionContext = ExecutionContext.global) extends EventStore[Future, ES] {

  implicit val timeout = Timeout(config.timeout)

  override def read(streamId: ES#Id,
                    fromVersion: Int,
                    maxCount: Int): Future[Seq[Event[ES]]] = {
    new EventStreamFetcher(streamId).read(fromVersion, maxCount)
  }

  override def persist(streamId: ES#Id,
                       events: Seq[ES#Payload],
                       expectedVersion: Option[Int]): Future[Seq[Event[ES]]] = {
    new EventStreamAppender(streamId).append(events, expectedVersion)
  }

  private class EventStreamFetcher(streamId: ES#Id) {
    def read(fromVersion: Int, maxCount: Int): Future[Seq[Event[ES]]] = {
      val readEvents = readEventsBuilder(fromVersion, maxCount)
      (connection ? readEvents.build)
        .mapTo[ReadStreamEventsCompleted]
        .map(toPage)
        .recover(noStreamToEmptyPage)
    }

    private def readEventsBuilder(fromVersion: Int, maxCount: Int): ReadStreamEventsBuilder = {
      val builder = new ReadStreamEventsBuilder(streamId.toString).maxCount(maxCount)
      if (fromVersion <= 1) builder.fromFirst
      else builder.fromNumber(fromVersion - 1)
      builder
    }

    private def toPage(result: ReadStreamEventsCompleted): Seq[Event[ES]] = {
      val events = result.events
      events.toStream.map(toEvent)
    }

    private def toEvent(event: eventstore.Event): Event[ES] = {
      val eventData = event.data
      val result = for {
        json <- parseByteBuffer(eventData.data)
        payload <- decoder.decodeJson(json)
      } yield {
        val version = event.number.value + 1
        val metaData = decode[EventMetaData](eventData.metadata).getOrElse(EventMetaData.Empty)
        Event(streamId, version, payload, metaData.timestamp)
      }
      result.toTry.get
    }

    private val noStreamToEmptyPage: PartialFunction[Throwable, Seq[Event[ES]]] = {
      case _: StreamNotFoundException => Stream.empty
    }
  }

  private class EventStreamAppender(streamId: ES#Id) {
    private val now = clock.instant()

    private val metaData = EventMetaData(now).asJson.noSpaces

    def append(events: Seq[ES#Payload], expectedVersion: Option[Int]): Future[Seq[Event[ES]]] = {
      val writeEvents = writeEventsBuilder(events, expectedVersion)
      val result = connection ? writeEvents.build
      result
        .mapTo[WriteEventsCompleted]
        .transform(
          getPersistedEvents(events),
          transformException)
    }

    private def writeEventsBuilder(events: Seq[ES#Payload], expectedVersion: Option[Int]) = {
      val eventsData = events.map(toEventData).asJava
      val builder = new WriteEventsBuilder(streamId.toString).addEvents(eventsData)
      expectedVersion match {
        case Some(version) if version == InitialVersion => builder.expectNoStream
        case Some(version) => builder.expectVersion(version - 1)
        case _ => builder.expectAnyVersion
      }
    }

    private def toEventData(event: ES#Payload): EventData = {
      new EventDataBuilder(event.getClass.getSimpleName)
        .data(encoder(event).noSpaces)
        .metadata(metaData)
        .build
    }

    private def getPersistedEvents(events: Seq[ES#Payload])
                                  (result: WriteEventsCompleted): Seq[Event[ES]] = {
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

}

object EventStoreEventStore {
  implicit def contentToBytes(content: Content): ByteBuffer = content.value.toByteBuffer

  implicit def contentToString(content: Content): String = content.value.utf8String

  implicit class EventRangeOps(range: EventNumber.Range) {
    def versions: Range = (range.start.value + 1) to (range.end.value + 1)
  }
}
