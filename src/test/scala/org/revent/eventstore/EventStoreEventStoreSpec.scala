package org.revent.eventstore

import java.time.Clock

import cats.instances.future.catsStdInstancesForFuture
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.revent.testkit.EventStoreFutureMatchers
import org.revent.{EventStoreContract, ExampleStream, MonadThrowable}

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future

class EventStoreEventStoreSpec extends EventStoreContract[Future] with EventStoreDockerTestKit {

  override def createStore(clock: Clock) = {
    val config = EventStoreConfig()

    new EventStoreEventStore[ExampleStream](
      connection,
      deriveEncoder,
      deriveDecoder,
      config,
      clock)(executionContext)
  }

  override val matchers = EventStoreFutureMatchers

  override val M: MonadThrowable[Future] =
    catsStdInstancesForFuture(executionContext)

}
