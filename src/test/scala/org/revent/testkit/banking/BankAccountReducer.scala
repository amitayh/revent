package org.revent.testkit.banking

import org.revent.Reducer.AggregateReducer
import org.revent.testkit.banking.domain.BankAccount

object BankAccountReducer extends AggregateReducer[BankAccountProtocol] {

  override val empty: BankAccount = BankAccount.Empty

  override def handle(account: BankAccount, event: BankAccountEvent): BankAccount = event match {
    case OwnerChanged(owner) => account.withOwner(owner)
    case DepositPerformed(amount) => account.deposit(amount)
    case WithdrawalPerformed(amount) => account.withdraw(amount)
    case _ => account
  }

}
