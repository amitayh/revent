package org.revent

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

import cats.instances.try_._
import org.revent.cqrs.{EventSourcedCommand, EventSourcedCommandHandler}
import org.revent.testkit.banking._
import org.revent.testkit.banking.domain.{BankAccount, BankAccountOwner}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.collection.immutable.Seq
import scala.collection.mutable.ListBuffer
import scala.util.Try

class EventSourcingIntegrationSpec extends Specification {

  "Event sourcing example" should {
    trait Context extends Scope {
      val now = Instant.now()
      val clock = Clock.fixed(now, ZoneOffset.UTC)
      val eventStore = new InMemoryEventStore[BankAccountStream](clock)
      val repository = new ReplayingAggregateRepository[Try, BankAccountProtocol](eventStore, BankAccountReducer)
      val handleCommand = new EventSourcedCommandHandler[Try, BankAccountProtocol](repository, eventStore)
      val publishEvents = new RecordingEventBus
      val handlingFunction = handleCommand andThen publishEvents

      val accountId = UUID.randomUUID()
      val owner = BankAccountOwner("John", "Doe")

      def handle(command: BankAccountCommand) = {
        handlingFunction(EventSourcedCommand[Try, BankAccountProtocol](accountId, command))
      }

      def beSnapshotWithAggregate[T](aggregate: T): Matcher[AggregateSnapshot[T]] = {
        equalTo(aggregate) ^^ { (_: AggregateSnapshot[T]).aggregate }
      }
    }

    "apply multiple commands to an aggregate" in new Context {
      handle(CreateBankAccount(owner, 10))
      handle(Deposit(100))
      handle(Deposit(20))
      handle(Withdraw(50))

      repository.load(accountId) must
        beSuccessfulTry(beSnapshotWithAggregate(BankAccount(owner, 80)))
    }

    "use multiple aggregates on same event stream" in new Context {
      handle(CreateBankAccount(owner, 20))
      handle(Withdraw(5))

      val balanceRepository = new ReplayingAggregateRepository[Try, BankAccountBalanceProtocol](eventStore, BalanceReducer)

      balanceRepository.load(accountId) must
        beSuccessfulTry(beSnapshotWithAggregate(15.0))
    }

    "publish successful events" in new Context {
      handle(CreateBankAccount(owner, 10))
      handle(Withdraw(20))

      publishEvents.play must equalTo(
        Event[BankAccountStream](accountId, 1, BankAccountCreated(), now) ::
        Event[BankAccountStream](accountId, 2, OwnerChanged(owner), now) ::
        Event[BankAccountStream](accountId, 3, DepositPerformed(10), now) :: Nil)
    }
  }

}

class RecordingEventBus extends (Try[Seq[Event[BankAccountStream]]] => Try[Unit]) {
  private val recordedEvents = ListBuffer.empty[Event[BankAccountStream]]

  override def apply(result: Try[Seq[Event[BankAccountStream]]]): Try[Unit] =
    result.map(recordedEvents appendAll _)

  def play: Seq[Event[BankAccountStream]] = recordedEvents.toList
}
