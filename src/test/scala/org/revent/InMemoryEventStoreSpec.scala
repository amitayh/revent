package org.revent

import cats.instances.try_._
import org.revent.testkit.EventStoreTryMatchers

import scala.util.Try

class InMemoryEventStoreSpec extends EventStoreContract[Try] {

  private val store = new InMemoryEventStore[ExampleStream](clock)

  override def createReader = store

  override def createWriter = store

  override val matchers = EventStoreTryMatchers

  override val monadInstance = catsStdInstancesForTry

}
