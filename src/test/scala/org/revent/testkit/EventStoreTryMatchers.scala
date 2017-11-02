package org.revent.testkit

import org.revent.EventStoreMatchers
import org.specs2.matcher.Matcher

import scala.reflect.ClassTag
import scala.util.Try

object EventStoreTryMatchers extends EventStoreMatchers[Try] {
  override def succeedWith(value: Matcher[Result]): Matcher[Try[Result]] = beSuccessfulTry(value)

  override def fail: Matcher[Try[Result]] = beFailedTry

  override def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[Try[Result]] =
    beFailedTry.withThrowable[E]
}
