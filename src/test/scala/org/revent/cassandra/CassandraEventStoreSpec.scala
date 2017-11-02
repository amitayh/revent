package org.revent.cassandra

import java.time.Clock

import cats.instances.future._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.revent.testkit.EventStoreFutureMatchers
import org.revent.{EventStoreContract, ExampleStream, MonadThrowable}

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.Future

class CassandraEventStoreSpec extends EventStoreContract[Future] with CassandraDockerTestKit {

  override val schema: Seq[String] = {
    val statements = s"""
       |CREATE KEYSPACE IF NOT EXISTS event_store
       |WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1};
       |
       |USE event_store;
       |
       |CREATE TABLE IF NOT EXISTS events (
       |  stream_id    TEXT,
       |  version      INT,
       |  event_type   TEXT,
       |  event_data   BLOB,
       |  event_time   BIGINT,
       |  max_version  INT STATIC,
       |  PRIMARY KEY (stream_id, version)
       |) WITH compression = {'sstable_compression': 'LZ4Compressor'};
     """.stripMargin

    statements.split(";").toList
  }

  override def createStore(clock: Clock) = {
    new CassandraEventStore[ExampleStream](
      session,
      "events",
      deriveEncoder,
      deriveDecoder,
      clock)(executionContext)
  }

  override val matchers = EventStoreFutureMatchers

  override val M: MonadThrowable[Future] =
    catsStdInstancesForFuture(executionContext)

}
