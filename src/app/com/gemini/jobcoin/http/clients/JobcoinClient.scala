package com.gemini.jobcoin.http.clients

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime

import com.gemini.jobcoin.JobcoinConfiguration
import com.gemini.jobcoin.http.HttpClient
import com.gemini.jobcoin.http.clients.json.Implicits.getAddressReads
import com.gemini.jobcoin.http.clients.json.Implicits.transactionReads
import com.gemini.jobcoin.http.clients.json.Implicits.transactionWrites
import com.gemini.jobcoin.models.Address
import com.gemini.jobcoin.models.Transaction

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject
import javax.inject.Singleton
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.ws.WSClient

@Singleton
class JobcoinClient @Inject()(
  config: JobcoinConfiguration,
  ws: WSClient,
  implicit val actorSystem: ActorSystem,
  implicit val ec: ExecutionContext,
  implicit val mat: Materializer
) extends HttpClient {

  def getAddress(id: String) = getImpl[Address](s"/addresses/$id")(getAddressReads(id))

  def getAllTransactions(id: String) = getImpl[Seq[Transaction]]("/transactions")

  def createTransaction(transaction: Transaction) = {
    val url = s"${config.HttpClient.BaseUrl}/transactions"
    val bodyAsJson = Json.toJson(transaction)

    val responseFuture = ws
      .url(url)
      .withRequestTimeout(config.HttpClient.HttpRequestTimeout)
      .post(bodyAsJson)

    logSendingRequestInfo(url, "POST", headersOpt = None, paramsOpt = None, bodyOpt = Some(Json.stringify(bodyAsJson)))
    val t0 = System.nanoTime()

    responseFuture.onSuccess {
      case response =>
        logReceivingResponseInfo(
          url,
          "POST",
          headersOpt = None,
          paramsOpt = None,
          t0,
          t1 = System.nanoTime(),
          response.status,
          Some(response.body.length),
          Some(response.body)
        )
    }

    responseFuture
      .map { response =>
        Try {
          (response.json \ "status").as[String] match {
            case "OK" =>
            case _ => throw new Exception("Unexpected response.")
          }
        } match {
          case Success(_) => ()
          case Failure(e) => throw e
        }
      }
  }

  private def getImpl[A](path: String)(implicit responseReads: Reads[A]): Future[A] = {
    val url = s"${config.HttpClient.BaseUrl}$path"
    val timestamp = DateTime.now.getMillis / 1000

    val responseFuture = ws
      .url(url)
      .withRequestTimeout(config.HttpClient.HttpRequestTimeout)
      .get

    logSendingRequestInfo(url, "GET", headersOpt = None, paramsOpt = None, bodyOpt = None)
    val t0 = System.nanoTime()

    responseFuture.onSuccess {
      case response =>
        logReceivingResponseInfo(
          url,
          "GET",
          headersOpt = None,
          paramsOpt = None,
          t0,
          t1 = System.nanoTime(),
          response.status,
          Some(response.body.length),
          Some(response.body)
        )
    }

    responseFuture
      .map { response =>
        Try(
          response.json
            .validate[A]
        ) match {
          case Success(JsSuccess(response, _)) => response
          case Success(JsError(e)) =>
            throw new Exception(s"Response could not be deserialized: ${e.toString}. Body: ${response.body}")
          case Failure(e) =>
            throw new Exception(s"Response could not be deserialized. Body: ${response.body}", e)
        }
      }

  }

}
