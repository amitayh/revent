package org.revent.testkit.banking

import org.revent.testkit.banking.domain.{BankAccount, BankAccountOwner}

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

sealed trait BankAccountCommand extends (BankAccount => Try[Seq[BankAccountEvent]])

case class CreateBankAccount(owner: BankAccountOwner, initialBalance: Double) extends BankAccountCommand {
  override def apply(account: BankAccount): Try[Seq[BankAccountEvent]] =
    Success(
      BankAccountCreated() ::
      OwnerChanged(owner) ::
      DepositPerformed(initialBalance) :: Nil)
}

case class Withdraw(amount: Double) extends BankAccountCommand {
  override def apply(account: BankAccount): Try[Seq[BankAccountEvent]] = {
    if (!account.withdrawalApproved(amount)) Failure(new BalanceTooLow(account.balance, amount))
    else Success(WithdrawalPerformed(amount) :: Nil)
  }
}

case class Deposit(amount: Double) extends BankAccountCommand {
  override def apply(account: BankAccount): Try[Seq[BankAccountEvent]] =
    Success(DepositPerformed(amount) :: Nil)
}

class BalanceTooLow(currentBalance: Double, requestedToWithdraw: Double)
  extends RuntimeException(s"Unable to withdraw amount $requestedToWithdraw (current balance $currentBalance)")
