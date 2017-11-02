package org.revent.testkit

import org.revent.EventStoreMatchers
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

object EventStoreFutureMatchers extends EventStoreMatchers[Future] {
  implicit val executionEnv = ExecutionEnv.fromExecutionContext(executionContext)

  private val retries = 2

  private val timeout = 2.seconds

  override def succeedWith(matcher: Matcher[Result]): Matcher[Future[Result]] =
    matcher.await(retries, timeout)

  override def fail: Matcher[Future[Result]] =
    throwA[Throwable].await(retries, timeout)

  override def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[Future[Result]] =
    throwA[E].await(retries, timeout)
}

