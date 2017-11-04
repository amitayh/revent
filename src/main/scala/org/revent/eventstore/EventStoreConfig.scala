package org.revent.eventstore

import org.revent.eventstore.EventStoreConfig._

import scala.concurrent.duration._

case class EventStoreConfig(timeout: FiniteDuration = DefaultTimeout)

object EventStoreConfig {
  val DefaultTimeout = 1.second
}
