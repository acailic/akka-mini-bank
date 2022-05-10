package com.acailic.bank.app

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.acailic.bank.actors.Bank
import com.acailic.bank.actors.PersistentBankAccount.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.acailic.bank.http.BankRouter
import  scala.util.{Failure,Try, Success}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
object BankApp {
   def startHttpServer(bank: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
     implicit val ex: ExecutionContext = system.executionContext
     val router = new BankRouter(bank)
     val routes = router.routes
     val httpBindingFuture =  Http().newServerAt("localhost", 8080).bind(routes)
     httpBindingFuture.onComplete {
       case Success(binding) =>
         system.log.info(s"HTTP server bound to $binding.localAddress")
       case Failure(e) =>
         system.log.error(s"Failed to bind HTTP server $e")
         system.terminate()
     }
   }

  def main(args: Array[String]) {
    println("Hello, world!")
    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
      val bankActor = context.spawn(Bank(), "bank")
      Behaviors.receiveMessage {
        case RetrieveBankActor(replyTo) =>
          replyTo ! bankActor
          Behaviors.same
      }
    }

      implicit val system = ActorSystem( rootBehavior, "bank-app")
      implicit val timeout = Timeout(5.seconds)
      implicit val ex: ExecutionContext = system.executionContext
      val bankActorFuture: Future[ActorRef[Command]]= system.ask(replyTo => RetrieveBankActor(replyTo))
      bankActorFuture.foreach(startHttpServer)
  }

}
