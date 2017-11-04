package org.revent

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class AggregateSnapshotSpec extends Specification {

  "Aggregate snapshot" should {
    trait Context extends Scope {
      val snapshot = AggregateSnapshot("foo", 1)
    }

    "conform to version if no version was provided" in new Context {
      snapshot.conformsTo(None) must beTrue
    }

    "conform to version if provided version matches snapshot version" in new Context {
      snapshot.conformsTo(Some(1)) must beTrue
    }

    "not conform to version if provided version doesn't match snapshot version" in new Context {
      snapshot.conformsTo(Some(2)) must beFalse
    }
  }

}
