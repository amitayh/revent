package org.revent.testkit.banking.domain

case class BankAccount(owner: BankAccountOwner, balance: Double) {

  def withOwner(newOwner: BankAccountOwner): BankAccount = copy(owner = newOwner)

  def deposit(amount: Double): BankAccount = copy(balance = balance + amount)

  def withdraw(amount: Double): BankAccount = copy(balance = balance - amount)

  def withdrawalApproved(amount: Double): Boolean = balance >= amount

}

object BankAccount {
  val Empty = BankAccount(null, 0)
}
