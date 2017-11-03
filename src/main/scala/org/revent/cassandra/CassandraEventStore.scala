package org.revent.cassandra

import java.time.{Clock, Instant}
import java.util.ConcurrentModificationException

import com.datastax.driver.core._
import io.circe.jawn.parseByteBuffer
import io.circe.{Decoder, Encoder, Printer}
import org.revent.AggregateSnapshot.InitialVersion
import org.revent.Event._
import org.revent.{Event, EventStore, EventStream}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class CassandraEventStore[ES <: EventStream]
  (session: Session,
   tableName: String,
   encoder: Encoder[ES#Payload],
   decoder: Decoder[ES#Payload],
   clock: Clock = Clock.systemUTC())
  (implicit executionContext: ExecutionContext = ExecutionContext.global) extends EventStore[Future, ES] {

  override def read(streamId: ES#Id,
                    fromVersion: Int,
                    maxCount: Int): Future[Seq[Event[ES]]] = Future {
    new EventStreamFetcher(streamId).fetch(fromVersion, maxCount)
  }

  override def persist(streamId: ES#Id,
                       events: Seq[ES#Payload],
                       expectedVersion: Option[Int]): Future[Seq[Event[ES]]] = Future {
    new EventStreamAppender(streamId).append(events, expectedVersion)
  }

  private class EventStreamFetcher(streamId: ES#Id) {

    private val selectEvents = session.prepare(
      s"""
         |SELECT version, event_type, event_data, event_time
         |FROM $tableName
         |WHERE stream_id = ?
         |AND version >= ?
         |ORDER BY version
         |LIMIT ?
    """.stripMargin
    )

    def fetch(fromVersion: Int, maxCount: Int): Seq[Event[ES]] = {
      val statement = selectEvents.bind(
        streamId.toString,
        Int.box(fromVersion),
        Int.box(maxCount))

      val result = session.execute(statement).asScala
      result.toStream.map(toEvent)
    }

    private def toEvent(row: Row): Event[ES] = {
      Event(
        streamId,
        row.getInt("version"),
        eventPayload(row).get,
        Instant.ofEpochMilli(row.getLong("event_time"))
      )
    }

    private def eventPayload(row: Row): Try[ES#Payload] = {
      parseByteBuffer(row.getBytes("event_data"))
        .flatMap(decoder.decodeJson)
        .toTry
    }

  }

  private class EventStreamAppender(streamId: ES#Id) {

    private val insertEvents = session.prepare(
      s"""
         |INSERT INTO $tableName (stream_id, version, event_type, event_data, event_time)
         |VALUES (?, ?, ?, ?, ?)
         |IF NOT EXISTS
    """.stripMargin
    )

    private val updateMaxVersion = session.prepare(
      s"""
         |UPDATE $tableName
         |SET max_version = ?
         |WHERE stream_id = ?
         |IF max_version = ?
    """.stripMargin
    )

    private val selectMaxVersion = session.prepare(
      s"SELECT max_version FROM $tableName WHERE stream_id = ? LIMIT 1")

    def append(events: Seq[ES#Payload],
               expectedVersionOption: Option[Int]): Seq[Event[ES]] = {
      val expectedVersion = expectedVersionOption.getOrElse(streamMaxVersion)
      val eventStream = events.toEventStream(streamId, expectedVersion, clock.instant())
      val batch = createBatch(expectedVersion, eventStream)
      val result = session.execute(batch)
      if (!result.wasApplied()) {
        throw new ConcurrentModificationException()
      }
      eventStream
    }

    private def createBatch(expectedVersion: Int, events: Seq[Event[ES]]): BatchStatement = {
      val initialBatch = createInitialBatch(expectedVersion, events.last.version)
      events.foldLeft(initialBatch) { (batch, event) =>
        batch.add(insertEventStatement(event))
      }
    }

    private def createInitialBatch(expectedVersion: Int, newMaxVersion: Int): BatchStatement = {
      val savedExpectedVersion = Option(expectedVersion).filter(_ > InitialVersion)
      val batch = new BatchStatement
      batch.add(
        updateMaxVersion.bind(
          Int.box(newMaxVersion),
          streamId.toString,
          savedExpectedVersion.map(Int.box).orNull
        )
      )
    }

    private def insertEventStatement(event: Event[ES]): Statement = {
      val payload = encoder(event.payload)
      insertEvents.bind(
        streamId.toString,
        Int.box(event.version),
        event.name,
        Printer.noSpaces.prettyByteBuffer(payload),
        Long.box(event.timestamp.toEpochMilli)
      )
    }

    private def streamMaxVersion: Int = {
      val result = session.execute(selectMaxVersion.bind(streamId.toString))
      Option(result.one()).fold(InitialVersion)(_.getInt("max_version"))
    }

  }

}
