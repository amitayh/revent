package org.revent

import java.time.Clock

import cats.instances.try_._
import org.specs2.matcher.Matcher

import scala.reflect.ClassTag
import scala.util.Try

class InMemoryEventStoreSpec extends EventStoreContract[Try] {

  override def createStore(clock: Clock) =
    new InMemoryEventStore[ExampleStream](clock)

  override val matchers = new EventStoreMatchers {
    override def succeed: Matcher[Try[Result]] = beSuccessfulTry
    override def succeedWith(value: Matcher[Result]): Matcher[Try[Result]] = beSuccessfulTry(value)
    override def fail: Matcher[Try[Result]] = beFailedTry
    override def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[Try[Result]] =
      beFailedTry.withThrowable[E]
  }

  override val M: MonadThrowable[Try] = catsStdInstancesForTry

}
