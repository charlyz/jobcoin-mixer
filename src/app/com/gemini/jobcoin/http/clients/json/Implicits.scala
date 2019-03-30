package com.gemini.jobcoin.http.clients.json

import org.joda.time.DateTime

import com.gemini.jobcoin.Implicits.RichDouble
import com.gemini.jobcoin.models.Address
import com.gemini.jobcoin.models.Transaction

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JodaReads.jodaDateReads
import play.api.libs.json.JodaWrites.jodaDateWrites
import play.api.libs.json.JsPath
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

object Implicits {
  
  val defaultDatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val defaultJodaDateWrites: Writes[DateTime] = jodaDateWrites(defaultDatePattern)
  implicit val defaultJodaDateReads: Reads[DateTime] = jodaDateReads(defaultDatePattern)
  
  implicit val transactionReads: Reads[Transaction] = (
    (JsPath \ "timestamp").read[DateTime] and
    (JsPath \ "toAddress").read[String] and
    (JsPath \ "fromAddress").readNullable[String] and
    (JsPath \ "amount").read[String]
  ) {
    (timestamp, toAddressId, fromAddressIdOpt, amountAsString) => 
      Transaction(timestamp, toAddressId, fromAddressIdOpt, amountAsString.toDouble.setScale(5))
  }
  
  implicit val transactionWrites: Writes[Transaction] = (
   (__ \ "timestamp").write[DateTime] and
   (__ \ "toAddress").write[String] and
   (__ \ "fromAddress").writeNullable[String] and
   (__ \ "amount").write[String]
  ) {
    transaction: Transaction =>
      (
        transaction.timestamp,
        transaction.toAddressId,
        transaction.fromAddressIdOpt,
        transaction.amount.toString
      )
  }
  
  def getAddressReads(id:String): Reads[Address] = (
    (JsPath \ "balance").read[String] and
    (JsPath \ "transactions").read[List[Transaction]]
  ) {
    (balanceAsString, transactions) => Address(id, balanceAsString.toDouble, transactions)
  }
  
  implicit val addressWrites: Writes[Address] = (
   (__ \ "balance").write[String] and
   (__ \ "transactions").write[Seq[Transaction]]
  ) {
    address: Address =>
      (
        address.balance.toString,
        address.transactions
      )
  }
  
}