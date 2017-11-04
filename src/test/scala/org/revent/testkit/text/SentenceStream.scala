package org.revent.testkit.text

import org.revent.{EventStream, Protocol}

import scala.collection.immutable.Seq

trait SentenceStream extends EventStream {
  override type Id = Int
  override type Payload = SentenceEvent
}

trait SentenceProtocol extends Protocol {
  override type EventStream = SentenceStream
  override type Aggregate = Seq[String]
}
