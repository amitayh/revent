package org.revent.testkit.banking.domain

case class BankAccountOwner(firstName: String, lastName: String) {
  def fullName = s"$firstName $lastName"
}
