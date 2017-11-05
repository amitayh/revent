package org.revent.cassandra

import com.datastax.driver.core.{Cluster, Session}
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.specs2.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerFactory, DockerReadyChecker}

import scala.collection.immutable.Seq
import scala.concurrent.duration._

trait CassandraDockerTestKit extends DockerTestKit {

  override val StartContainersTimeout = 1.minute

  private val cassandraHost = "127.0.0.1"

  private val cqlPort = 9042

  val cassandraContainer = DockerContainer("cassandra:3.10")
    .withPorts(cqlPort -> Some(cqlPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Starting listening for CQL clients on"))

  def schema: Seq[String]

  override implicit val dockerFactory: DockerFactory = {
    val client = DefaultDockerClient.fromEnv().build()
    new SpotifyDockerFactory(client)
  }

  lazy val session: Session = {
    val sess = Cluster.builder()
      .addContactPoint(cassandraHost)
      .build()
      .connect()

    schema
      .map(_.trim)
      .filter(_.nonEmpty)
      .foreach(sess.execute)

    sess
  }

  override def dockerContainers: List[DockerContainer] =
    cassandraContainer :: super.dockerContainers

}
