package org.revent.cassandra

import java.time.Clock
import java.util.ConcurrentModificationException

import com.datastax.driver.core._
import io.circe.{Encoder, Printer}
import org.revent.Event._
import org.revent._

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

class CassandraEventStoreWriter[ES <: EventStream]
  (session: Session,
   tableName: String,
   encoder: Encoder[ES#Payload],
   clock: Clock = Clock.systemUTC())
  (implicit executionContext: ExecutionContext = ExecutionContext.global)
  extends EventStreamWriter[Future, ES] {

  private val insertEvents = session.prepare(
    s"""
      |INSERT INTO $tableName (stream_id, version, event_type, event_data, event_time)
      |VALUES (?, ?, ?, ?, ?)
      |IF NOT EXISTS
      """.stripMargin)

  private val updateMaxVersion = session.prepare(
    s"""
      |UPDATE $tableName
      |SET max_version = ?
      |WHERE stream_id = ?
      |IF max_version = ?
      """.stripMargin)

  private val selectMaxVersion = session.prepare(
    s"SELECT max_version FROM $tableName WHERE stream_id = ? LIMIT 1")

  override def persist(streamId: ES#Id,
                       events: Seq[ES#Payload],
                       expectedVersion: Option[Version]): Future[Seq[Event[ES]]] = {
    if (events.isEmpty) Future.successful(Nil)
    else expectedVersionFor(streamId, expectedVersion)
      .flatMap(persist(streamId, events))
  }

  private def persist(streamId: ES#Id, events: Seq[ES#Payload])
                     (expectedVersion: Version): Future[Seq[Event[ES]]] = Future {
    val eventStream = events.toEventStream(streamId, expectedVersion, clock.instant())
    val batch = createBatch(eventStream, expectedVersion)
    val result = session.execute(batch)
    if (!result.wasApplied()) {
      throw new ConcurrentModificationException(s"Unable to persist events to stream $streamId")
    }
    eventStream
  }

  private def createBatch(events: Seq[Event[ES]], expectedVersion: Version): BatchStatement = {
    val lastEvent = events.last
    val initialBatch = createInitialBatch(lastEvent.streamId, expectedVersion, lastEvent.version)
    events.foldLeft(initialBatch) { (batch, event) =>
      batch.add(insertEventStatement(event))
    }
  }

  private def createInitialBatch(streamId: ES#Id,
                                 expectedVersion: Version,
                                 newMaxVersion: Version): BatchStatement = {
    val savedExpectedVersion = if (expectedVersion.isFirst) None else Some(expectedVersion)
    val batch = new BatchStatement
    batch.add(
      updateMaxVersion.bind(
        Int.box(newMaxVersion),
        streamId.toString,
        savedExpectedVersion
          .map(version => Int.box(version))
          .orNull
      )
    )
  }

  private def insertEventStatement(event: Event[ES]): Statement = {
    val payload = encoder(event.payload)
    insertEvents.bind(
      event.streamId.toString,
      Int.box(event.version),
      event.name,
      Printer.noSpaces.prettyByteBuffer(payload),
      Long.box(event.timestamp.toEpochMilli)
    )
  }

  private def expectedVersionFor(streamId: ES#Id,
                                 expectedVersion: Option[Version]): Future[Version] = {
    expectedVersion.fold(streamMaxVersion(streamId))(Future.successful)
  }

  private def streamMaxVersion(streamId: ES#Id): Future[Version] = Future {
    val result = session.execute(selectMaxVersion.bind(streamId.toString))
    Option(result.one()).fold(Version.First)(_.getInt("max_version"))
  }

}
