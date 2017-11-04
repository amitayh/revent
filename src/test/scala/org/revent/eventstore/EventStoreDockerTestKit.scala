package org.revent.eventstore

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.specs2.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerFactory, DockerReadyChecker}
import eventstore.j.SettingsBuilder
import eventstore.tcp.ConnectionActor

trait EventStoreDockerTestKit extends DockerTestKit {

  private val eventStoreHost = "127.0.0.1"

  private val eventStorePort = 1113

  private val eventStoreUser = "admin"

  private val eventStorePass = "changeit"

  private val eventStoreContainer = DockerContainer("eventstore/eventstore")
    .withPorts(eventStorePort -> Some(eventStorePort))
    .withReadyChecker(DockerReadyChecker.LogLineContains(s"HTTP server is up and listening on [http://*:2113/]"))

  private var system: ActorSystem = _

  override implicit val dockerFactory: DockerFactory = {
    val client = DefaultDockerClient.fromEnv().build()
    new SpotifyDockerFactory(client)
  }

  lazy val connection: ActorRef = {
    val settings = new SettingsBuilder()
      .address(new InetSocketAddress(eventStoreHost, eventStorePort))
      .defaultCredentials(eventStoreUser, eventStorePass)
      .build

    system.actorOf(ConnectionActor.getProps(settings))
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    system = ActorSystem.create()
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  override def dockerContainers: List[DockerContainer] =
    eventStoreContainer :: super.dockerContainers

}
