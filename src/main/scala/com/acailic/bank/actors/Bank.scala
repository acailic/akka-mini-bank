package com.acailic.bank.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.util.UUID

object Bank {
  // command  = messages

  import PersistentBankAccount._
  import PersistentBankAccount.Command._
  import PersistentBankAccount.Command

  // events
  sealed trait Event

  case class BankAccountCreated(id: String) extends Event


  // state
  case class State(accounts: Map[String, ActorRef[Command]])


  // command handler
  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case createCommand@CreateBankAccount(_, _, _, _) =>
        val id = UUID.randomUUID().toString
        val newBankAccount = context.spawn(PersistentBankAccount(id), id)
        Effect.persist(BankAccountCreated(id))
          .thenReply(newBankAccount)(_ => createCommand)

      case updateCommand@UpdateBalance(id, currency, amount, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(updateCommand)
          case None =>
            Effect.reply(replyTo)(BankAccountBalanceUpdated(None))
        }

    }


  val eventHandler: (State, Event) => State = ???

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler
    )

    //event handler

  }
