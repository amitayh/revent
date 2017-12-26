package org.revent.cassandra

import java.time.Instant

import com.datastax.driver.core._
import io.circe.Decoder
import io.circe.jawn.parseByteBuffer
import org.revent._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class CassandraEventStreamReader[ES <: EventStream]
  (session: Session, tableName: String, decoder: Decoder[ES#Payload])
  (implicit executionContext: ExecutionContext = ExecutionContext.global)
  extends EventStreamReader[Future, ES] {

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

  override def read(streamId: ES#Id,
                    fromVersion: Version,
                    maxCount: Int): Future[Seq[Event[ES]]] = Future {
    val statement = selectEvents.bind(
      streamId.toString,
      Int.box(fromVersion),
      Int.box(maxCount))

    val result = session.execute(statement).asScala
    result.toStream.map(toEvent(streamId))
  }

  private def toEvent(streamId: ES#Id)(row: Row): Event[ES] = {
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
