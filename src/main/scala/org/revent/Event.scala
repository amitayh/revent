package org.revent

import java.time.Instant

import scala.collection.immutable.Seq

case class Event[ES <: EventStream](streamId: ES#Id,
                                    version: Version,
                                    payload: ES#Payload,
                                    timestamp: Instant) {
  def name: String = payload.getClass.getSimpleName
}

object Event {
  implicit class EventStreamOps[ES <: EventStream](payloads: Seq[ES#Payload]) {
    def toEventStream(streamId: ES#Id, lastVersion: Version, timestamp: Instant): Seq[Event[ES]] = {
      (lastVersion.nextVersions zip payloads).map {
        case (version, payload) => Event(streamId, version, payload, timestamp)
      }
    }
  }
}
