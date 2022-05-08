package com.acailic.bank.http

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.acailic.bank.actors.PersistentBankAccount.Command._
import com.acailic.bank.actors.PersistentBankAccount.Response._
import com.acailic.bank.actors.PersistentBankAccount.{BankAccount, Command, Response}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt;
// based on macro

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

case class BankAccountCreateRequest(user: String, currency: String, balance: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    CreateBankAccount(user, currency, balance, replyTo)
}

case class FailureResponse(message: String)
case class BankAccountUpdateRequest(currency: String, amount: Double) {
  def toCommand(id: String, replyTo: ActorRef[Response]): Command = UpdateBalance(id, currency, amount, replyTo)
}

class BankRouter(bank: ActorRef[Command])(implicit system: ActorSystem[_]) {

  implicit val timeout: Timeout = Timeout(5.seconds)

  def getBankAccount(id: String): Future[Response] = {
    bank.ask(replyTo => GetBankAccount(id, replyTo))
  }
  def updateBankAccount(id: String,request: BankAccountUpdateRequest): Future[Response] = {
    bank.ask(replyTo => request.toCommand(id, replyTo))
  }


  def createBankAccount(request: BankAccountCreateRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(replyTo))
  //#all-routes
  /** *
   * Post /bank/
   * 201 Created  /bank/<id>
   */
  /** *
   * GET /bank/uuid
   * 200 OK json  repr bank account
   */
  /** *
   *  PUT /bank/uuid
   *  200 OK json  currency and balance
   *  400 Bad Request
   *    - currency not supported
   */
  //* All routes defined here
  val routes: Route =
  pathPrefix("bank") {
    pathEndOrSingleSlash {
      post {
        //parse payload
        entity(as[BankAccountCreateRequest]) {
          request =>
            // convert request into a command
            //send command to bank
            // expect a response
            // parse reply and send back http response
            onSuccess(createBankAccount(request)) {
              case BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
            }
        }
      }
    } ~
      path(Segment) { id =>
        get {
          /** * */
          onSuccess(getBankAccount(id)) {
            case GetBankAccountResponse(Some(account)) => complete(account)
            case GetBankAccountResponse(None) => complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id not found"))

          }
        } ~ put {

          entity(as[BankAccountUpdateRequest]) { request =>
          onSuccess(updateBankAccount(id, request)) {
            case BankAccountBalanceUpdated(Some(account)) => complete(account)
            case BankAccountBalanceUpdated(None) => complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id not found"))
          }
        }
        }
      }

  }
