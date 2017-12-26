package org.revent.eventstore

import java.nio.ByteBuffer

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import eventstore.j.ReadStreamEventsBuilder
import eventstore.{Content, ReadStreamEventsCompleted, StreamNotFoundException}
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.jawn.parseByteBuffer
import io.circe.parser._
import org.revent.eventstore.EventStoreEventStreamReader._
import org.revent.{Event, EventStream, EventStreamReader, Version}

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class EventStoreEventStreamReader[ES <: EventStream]
  (connection: ActorRef, decoder: Decoder[ES#Payload], config: EventStoreConfig)
  (implicit executionContext: ExecutionContext = ExecutionContext.global)
  extends EventStreamReader[Future, ES] {

  implicit val timeout = Timeout(config.timeout)

  override def read(streamId: ES#Id,
                    fromVersion: Version,
                    maxCount: Int): Future[Seq[Event[ES]]] = {
    val readEvents = readEventsBuilder(streamId, fromVersion, maxCount)
    (connection ? readEvents.build)
      .mapTo[ReadStreamEventsCompleted]
      .map(toPage(streamId))
      .recover(noStreamToEmptyPage)
  }

  private def readEventsBuilder(streamId: ES#Id,
                                fromVersion: Version,
                                maxCount: Int): ReadStreamEventsBuilder = {
    val builder = new ReadStreamEventsBuilder(streamId.toString).maxCount(maxCount)
    if (fromVersion <= 1) builder.fromFirst
    else builder.fromNumber(fromVersion - 1)
    builder
  }

  private def toPage(streamId: ES#Id)
                    (result: ReadStreamEventsCompleted): Seq[Event[ES]] = {
    val events = result.events
    events.toStream.map(toEvent(streamId))
  }

  private def toEvent(streamId: ES#Id)
                     (eventStoreEvent: eventstore.Event): Event[ES] = {
    val eventData = eventStoreEvent.data
    val result = for {
      json <- parseByteBuffer(eventData.data)
      payload <- decoder.decodeJson(json)
    } yield {
      val version = eventStoreEvent.number.value + 1
      val metaData = decode[EventMetaData](eventData.metadata).getOrElse(EventMetaData.Empty)
      Event(streamId, version, payload, metaData.timestamp)
    }
    result match {
      case Right(event) => event
      case Left(e) => throw e
    }
  }

  private val noStreamToEmptyPage: PartialFunction[Throwable, Seq[Event[ES]]] = {
    case _: StreamNotFoundException => Stream.empty
  }

}

object EventStoreEventStreamReader {
  implicit def contentToBytes(content: Content): ByteBuffer = content.value.toByteBuffer

  implicit def contentToString(content: Content): String = content.value.utf8String
}
