package org

import cats.MonadError

import scala.language.higherKinds

package object revent {
  type MonadThrowable[F[_]] = MonadError[F, Throwable]
}
