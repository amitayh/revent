# Revent [![Build Status](https://travis-ci.org/amitayh/revent.svg?branch=master)](https://travis-ci.org/amitayh/revent)

Revent is a lightweight event-sourcing library for Scala based on
[cats](https://github.com/typelevel/cats), designed to stay out of your way

The name "revent" is a [portmanteau](https://en.wikipedia.org/wiki/Portmanteau) for *reduce* + 
*event*

## Event Sourcing 101

Event sourcing is an architectural pattern in which you model your application dat, by capturing a
sequence of immutable events. The topic was discussed in length by people like
[Greg Young](https://twitter.com/gregyoung), [Udi Dahan](https://twitter.com/UdiDahan),
[Martin Fowler](https://twitter.com/martinfowler) and others. It is recommended to study the
benefits and disadvantages of using event sourcing beforehand.

Recommended materials:

 * https://www.youtube.com/watch?v=8JKjvY4etTY
 * https://martinfowler.com/eaaDev/EventSourcing.html

## Main Components

### Protocols

A protocol defines a particular view over an event stream. Let's take a closer look at the 
`Protocol` interface:

```scala
trait Protocol {
  type EventStream <: org.revent.EventStream
  type Aggregate
}

trait EventStream {
  type Id
  type Payload
}
```

When defining a protocol, you must supply the types for the event stream ID, the event payload, 
and the aggregate itself.

In the bank account example, as used in the
[tests](src/test/scala/org/revent/EventSourcingIntegrationSpec.scala), the protocol definition
is as follows:

```scala
trait BankAccountStream extends EventStream {
  override type Id = UUID
  override type Payload = BankAccountEvent
}

trait BankAccountProtocol extends Protocol {
  override type EventStream = BankAccountStream
  override type Aggregate = BankAccount
}
```

We see that the aggregate ID (every event belongs to some unique aggregate) is a `UUID`, each event
in the stream is a subclass of `BankAccountEvent`, and the aggregate itself is of type 
`BankAccount`. We can use the same event stream with other protocols as well, as shown with the 
`BankAccountBalanceProtocol`
[here](src/test/scala/org/revent/testkit/banking/BankAccountProtocol.scala).

### Reducers

Reducers are used to reduce an event stream into a single value. In most cases, you'll need to
define aggregate reducers, which reduce the event stream into your aggregate type (as defined in
your `Protocol`)

Continuing with the bank account example, a reducer might look like this:

```scala
object BankAccountReducer extends AggregateReducer[BankAccountProtocol] {
  override val empty: BankAccount = BankAccount.Empty

  override def handle(account: BankAccount, event: BankAccountEvent): BankAccount = event match {
    case OwnerChanged(owner) => account.withOwner(owner)
    case DepositPerformed(amount) => account.deposit(amount)
    case WithdrawalPerformed(amount) => account.withdraw(amount)
    case _ => account
  }
}
```

As seen here, 2 methods need to be implemented:

 * `empty` - which returns an initial value for the aggregate
 * `handle(aggregate, event)` - which takes an aggregate and an event, and returns a new version of
  the aggregate with the event applied

> Note: you must always return an aggregate. If the new event has no effect on the aggregate,
  return the aggregate as is

### The Event Store

The event store abstraction is used to persist and read event streams. It's comprised of two traits:
`EventStreamReader[F[_], ES <: EventStream]` and `EventStreamWriter[F[_], ES <: EventStream]`. They
are both parameterized by 2 type arguments
  * `F[_]` - is the type constructor of the target monad. This allows the event store to be either
    synchronous (by using `Try` or `Either`, for example) or asynchronous (by using `Future`)
  * `ES` - is the type of the event stream the store is meant to read and write. This may have
    implications on the way used to serialize and deserialize the stream ID and event payloads

Several implementations are already provided:

 * Async store based on [Apache's Cassandra](http://cassandra.apache.org/) -
   [here](src/main/scala/org/revent/cassandra)
 * Async store based on [Greg Young's Event Store](https://www.geteventstore.com/) -
   [here](src/main/scala/org/revent/eventstore)
 * Sync in memory implementation (useful for testing) -
   [here](src/main/scala/org/revent/InMemoryEventStore.scala)

### The Aggregate Repository

The aggregate repository can be used to retrieve the current state of the aggregate. The
`ReplayingAggregateRepository` uses the reducer to replay all past events for the aggregate, reduced
to the current state.

For optimization, a `SnapshottingAggregateRepository` (WIP) can be used. It will periodically take
snapshots of the aggregate's state to prevent replaying the entire event stream for each request.

## CQRS

The library includes simple [CQRS](https://martinfowler.com/bliki/CQRS.html)-style command handling.
The `EventSourcedCommandHandler` will execute an `EventSourcedCommand` by running these steps:

  1. Fetch current state of the aggregate from the aggregate repository
  2. Apply the aggregate to the command to produce new events
  3. Persist events to the event store

During this process, optimistic locking is used to verify consistency.

Example [commands](src/test/scala/org/revent/testkit/banking/BankAccountCommand.scala) and their
[usage](src/test/scala/org/revent/EventSourcingIntegrationSpec.scala) can be found in the tests.

## License

Copyright © 2017 Amitay Horwitz

Distributed under the MIT License
