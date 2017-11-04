package org.revent.testkit.banking

import org.revent.Reducer.AggregateReducer

object BalanceReducer extends AggregateReducer[BankAccountBalanceProtocol] {

  override val empty: Double = 0

  override def handle(balance: Double, event: BankAccountEvent): Double = event match {
    case DepositPerformed(amount) => balance + amount
    case WithdrawalPerformed(amount) => balance - amount
    case _ => balance
  }

}
