package com.acailic.bank.actors

/*
Aktor typed ref
 */

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.acailic.bank.actors.PersistentBankAccount.Response._

import scala.util.{Failure, Success, Try}

// a single bank account
// event sourcing
object PersistentBankAccount {

  /*

    Fault tolerant
    - auditing
    - revisit events
    */
  sealed trait Command

  object Command {
    //Commands = messages
    case class CreateBankAccount(user: String, currency: String, initialBalance: BigDecimal, replyTo: ActorRef[Response]) extends Command

    case class UpdateBalance(id: String, currency: BigDecimal, amount: Double, /*can be negative*/ replyTo: ActorRef[Response]) extends Command

    case class GetBankAccount(id: String, replyTo: ActorRef[Response]) extends Command
  }

  import Command._

  //events
  trait Event

  case class BankAccountCreated(bankAccount: BankAccount) extends Event

  case class BalanceUpdated(amount: Double) extends Event


  //state
  case class BankAccount(id: String, user: String, currency: String, balance: BigDecimal)

  //responses
  sealed trait Response
  object Response {
    case class BankAccountCreatedResponse(id: String) extends Response
    case class BankAccountBalanceUpdated(maybeBankAccount: Option[BankAccount]) extends Response
    case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount]) extends Response
  }
  /*
  Commands = messages
  events = to persist in Casandra
  state
  responses
   */


  // Command handler = message handler => persistent event
  // Event handler => update state
  // State

  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] = (state, command) =>
    command match {
      /*
      Bank creates me  -> bank sends me created account -> persist created -> update state -> send response -> Bank surfaces http response
       */
      case CreateBankAccount(user, currency, initialBalance, bank) =>
        val id = state.id
        Effect.persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance))) // into casandra
          .thenReply(bank)(_ => BankAccountCreatedResponse(id)) //lambda to reply to the actor
      case UpdateBalance(_, _, amount, bank) =>
        val newBalance = state.balance + amount
        // check for withdrawals
        if (newBalance < 0) { // illegal
          Effect.reply(bank)(BankAccountBalanceUpdated(None))
        } else {
          Effect.persist(BalanceUpdated(amount))
            .thenReply(bank)(newState => BankAccountBalanceUpdated(Some(newState)))
        }
      case GetBankAccount(_, replyTo) =>
        Effect.reply(replyTo)(GetBankAccountResponse(Some(state)))
    }

  val eventHandler: (BankAccount, Event) => BankAccount = (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) => bankAccount
      case BalanceUpdated(amount) =>
        val newBalance = state.balance + amount
        state.copy(balance = newBalance)
    }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId("account", id),
      emptyState = BankAccount(id, "", "", 0.0), // unused
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )

}
