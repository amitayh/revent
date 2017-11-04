package org.revent

trait Protocol {
  type EventStream <: org.revent.EventStream
  type Aggregate

  type EventStreamId = EventStream#Id
  type EventPayload = EventStream#Payload
}
