package org.revent.testkit.banking

import org.revent.testkit.banking.domain.BankAccountOwner

sealed trait BankAccountEvent

case class BankAccountCreated() extends BankAccountEvent

case class OwnerChanged(owner: BankAccountOwner) extends BankAccountEvent

case class DepositPerformed(amount: Double) extends BankAccountEvent

case class WithdrawalPerformed(amount: Double) extends BankAccountEvent
