package com.acailic.bank.http

case class BankAccountCreationRequest(name: String, initialBalance: BigDecimal)

class BankRouter {

  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.Route

  val route: Route =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        get {
          complete(bankService.getAll)
        }
      } ~
        pathPrefix(Segment) { id =>
          pathEndOrSingleSlash {
            get {
              complete(bankService.get(id))
            }
          } ~
            path("accounts") {
              get {
                complete(bankService.getAccounts(id))
              }
            }
        }
    }

}
