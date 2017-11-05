package org.revent.eventstore

import cats.instances.future.catsStdInstancesForFuture
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.revent._
import org.revent.testkit.EventStoreFutureMatchers

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.concurrent.duration._

class EventStoreEventStoreSpec
  extends EventStoreContract[Future]
    with EventStoreDockerTestKit {

  private lazy val store = {
    val config = EventStoreConfig(timeout = 5.seconds)
    new EventStoreEventStore[ExampleStream](
      connection,
      deriveEncoder,
      deriveDecoder,
      config,
      clock)(executionContext)
  }

  override def createReader = store

  override def createWriter = store

  override val matchers = EventStoreFutureMatchers

  override val monadInstance =
    catsStdInstancesForFuture(executionContext)

}
