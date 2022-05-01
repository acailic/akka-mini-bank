package com.acailic.bank.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import java.util.UUID
import scala.concurrent.ExecutionContext

object Bank {
  // command  = messages
  import PersistentBankAccount.Command._
  import PersistentBankAccount.Command
  import PersistentBankAccount.Response._

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

      case updateCommand@UpdateBalance(id, _, _, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(updateCommand)
          case None =>
            Effect.reply(replyTo)(BankAccountBalanceUpdated(None)) //failed
        }
      case getCmd @ GetBankAccount(id,replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(getCmd)
          case None =>
            Effect.reply(replyTo)(GetBankAccountResponse(None)) //failed
        }

    }


  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case BankAccountCreated(id) =>
        // update state
        val account = context.child(id)
          .getOrElse(context.spawn(PersistentBankAccount(id), id)) //exists after command handler, does not exist in recovery mode
          .asInstanceOf[ActorRef[Command]]
        state.copy(state.accounts + (id -> account))
    }

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context),
    )

    //event handler
  }


}

object BankPlayground {
  import  scala.concurrent.duration._
  import PersistentBankAccount.Command._
  import PersistentBankAccount.Response._

  def main(args: Array[String]): Unit = {
    val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
      val bank = context.spawn(Bank(), "bank")
      // ask pattern
      import akka.actor.typed.scaladsl.AskPattern._
      import scala.concurrent.duration._

      implicit val timeout: Timeout = Timeout(2.seconds)
      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val ec: ExecutionContext = context.executionContext

      bank.ask(replyTo => CreateBankAccount( "John", "EUR", 100, replyTo)).flatMap{
        case BankAccountCreatedResponse(id) =>
          context.log.info(s"Bank account created: $id")
          bank.ask(replyTo => GetBankAccount(id, replyTo))
      }.foreach{
        case GetBankAccountResponse(maybeBankAccount) =>
          context.log.info(s"Bank account: $maybeBankAccount")
      }
      Behaviors.empty
    }

    val system = ActorSystem(rootBehavior, "BankPlayground")
  }
}
