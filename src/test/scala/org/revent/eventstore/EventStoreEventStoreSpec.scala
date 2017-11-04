package org.revent.eventstore

import java.time.Clock

import cats.instances.future.catsStdInstancesForFuture
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.revent.{EventStoreContract, ExampleStream, MonadThrowable}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.reflect.ClassTag

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

  override val matchers = new EventStoreMatchers {
    implicit val executionEnv = ExecutionEnv.fromExecutionContext(executionContext)
    override def succeedWith(matcher: Matcher[Result]): Matcher[Future[Result]] = matcher.await
    override def fail: Matcher[Future[Result]] = throwA[Throwable].await
    override def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[Future[Result]] =
      throwA[E].await
  }

  override val M: MonadThrowable[Future] =
    catsStdInstancesForFuture(executionContext)

}
