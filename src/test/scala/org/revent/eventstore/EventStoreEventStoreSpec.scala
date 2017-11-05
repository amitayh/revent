package org.revent.eventstore

import java.time.Clock

import cats.instances.future.catsStdInstancesForFuture
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.revent.testkit.EventStoreFutureMatchers
import org.revent.{EventStoreContract, ExampleStream, MonadThrowable}

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.concurrent.duration._

class EventStoreEventStoreSpec extends EventStoreContract[Future] with EventStoreDockerTestKit {

  override def createStore(clock: Clock) = {
    val config = EventStoreConfig(timeout = 5.seconds)

    new EventStoreEventStore[ExampleStream](
      connection,
      deriveEncoder,
      deriveDecoder,
      config,
      clock)(executionContext)
  }

  override val matchers = EventStoreFutureMatchers

  override val monadInstance: MonadThrowable[Future] =
    catsStdInstancesForFuture(executionContext)

}
