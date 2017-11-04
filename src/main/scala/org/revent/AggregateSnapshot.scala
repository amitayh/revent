package org.revent

import java.time.Instant

import scala.language.higherKinds

case class AggregateSnapshot[Aggregate](aggregate: Aggregate,
                                        version: Int,
                                        timestamp: Option[Instant] = None) {

  def conformsTo(expectedVersion: Option[Int]): Boolean =
    expectedVersion.forall(_ == version)

}

object AggregateSnapshot {
  val InitialVersion = 0
}
