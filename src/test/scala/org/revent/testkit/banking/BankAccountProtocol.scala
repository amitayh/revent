package org.revent.testkit.banking

import java.util.UUID

import org.revent.testkit.banking.domain.BankAccount
import org.revent.{EventStream, Protocol}

trait BankAccountStream extends EventStream {
  override type Id = UUID
  override type Payload = BankAccountEvent
}

trait BankAccountProtocol extends Protocol {
  override type EventStream = BankAccountStream
  override type Aggregate = BankAccount
}

trait BankAccountBalanceProtocol extends Protocol {
  override type EventStream = BankAccountStream
  override type Aggregate = Double
}
