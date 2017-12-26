package org.revent

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

import cats.data.Kleisli
import cats.implicits._
import org.revent.cqrs.{CommandHandled, EventSourcedCommand, EventSourcedCommandHandler}
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
      type EventStore = InMemoryEventStore[BankAccountStream]
      type Repository = ReplayingAggregateRepository[Try, BankAccountProtocol]
      type CommandHandler = EventSourcedCommandHandler[Try, BankAccountProtocol]
      type Command = EventSourcedCommand[Try, BankAccountProtocol]

      val now = Instant.now()
      val clock = Clock.fixed(now, ZoneOffset.UTC)
      val eventStore = new EventStore(clock)
      val repository = new Repository(eventStore, BankAccountReducer)
      val handleCommand = new CommandHandler(repository, eventStore, BankAccountReducer)
      val publishEvents = new RecordingEventBus
      val handlingFunction = Kleisli(handleCommand) andThen Kleisli(publishEvents)

      val accountId = UUID.randomUUID()
      val owner = BankAccountOwner("John", "Doe")

      def handle(command: BankAccountCommand): Try[Unit] = {
        handlingFunction(new Command(accountId, command))
      }

      def beSnapshotWithAggregate[T](aggregate: T): Matcher[AggregateSnapshot[T]] = {
        equalTo(aggregate) ^^ { (_: AggregateSnapshot[T]).aggregate }
      }
    }

    "apply multiple commands to an aggregate" in new Context {
      val result =
        handle(CreateBankAccount(owner, 10)) >>
          handle(Deposit(100)) >>
          handle(Deposit(20)) >>
          handle(Withdraw(50)) >>
          repository.load(accountId)

      result must beSuccessfulTry(beSnapshotWithAggregate(BankAccount(owner, 80)))
    }

    "use multiple aggregates on same event stream" in new Context {
      type BalanceRepository = ReplayingAggregateRepository[Try, BankAccountBalanceProtocol]

      val balanceRepository = new BalanceRepository(eventStore, BalanceReducer)

      val result =
        handle(CreateBankAccount(owner, 20)) >>
          handle(Withdraw(5)) >>
          balanceRepository.load(accountId)

      result must beSuccessfulTry(beSnapshotWithAggregate(15.0))
    }

    "publish successful events" in new Context {
      handle(CreateBankAccount(owner, 10)) >> handle(Withdraw(20))

      publishEvents.play must equalTo(
        Event[BankAccountStream](accountId, 1, BankAccountCreated(), now) ::
        Event[BankAccountStream](accountId, 2, OwnerChanged(owner), now) ::
        Event[BankAccountStream](accountId, 3, DepositPerformed(10), now) :: Nil)
    }
  }

}

class RecordingEventBus extends (CommandHandled[BankAccountProtocol] => Try[Unit]) {
  private val recordedEvents = ListBuffer.empty[Event[BankAccountStream]]

  override def apply(result: CommandHandled[BankAccountProtocol]): Try[Unit] =
    Try(recordedEvents appendAll result.persistedEvents)

  def play: Seq[Event[BankAccountStream]] = recordedEvents.toList
}
