package com.gemini.jobcoin.models

import org.joda.time.DateTime

case class Transaction(
  timestamp: DateTime,
  toAddressId: String,
  fromAddressIdOpt: Option[String],
  amount: Double
)
