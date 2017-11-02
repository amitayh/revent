package org.revent

import java.time.Clock

import cats.instances.try_._
import org.revent.testkit.EventStoreTryMatchers

import scala.util.Try

class InMemoryEventStoreSpec extends EventStoreContract[Try] {

  override def createStore(clock: Clock) =
    new InMemoryEventStore[ExampleStream](clock)

  override val matchers = EventStoreTryMatchers

  override val monadInstance: MonadThrowable[Try] = catsStdInstancesForTry

}
