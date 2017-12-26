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

  val config = EventStoreConfig(timeout = 5.seconds)

  override def createReader =
    new EventStoreEventStreamReader[ExampleStream](
      connection,
      deriveDecoder,
      config)(executionContext)

  override def createWriter =
    new EventStoreEventStreamWriter[ExampleStream](
      connection,
      deriveEncoder,
      config,
      clock)(executionContext)

  override val matchers = EventStoreFutureMatchers

  override val monadInstance =
    catsStdInstancesForFuture(executionContext)

}
