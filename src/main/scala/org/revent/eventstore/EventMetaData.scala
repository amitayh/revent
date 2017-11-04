package org.revent.eventstore

import java.time.Instant

case class EventMetaData(timestampMillis: Long) {
  def timestamp: Instant = Instant.ofEpochMilli(timestampMillis)
}

case object EventMetaData {
  val Empty = EventMetaData(0L)

  def apply(instant: Instant): EventMetaData = EventMetaData(instant.toEpochMilli)
}
