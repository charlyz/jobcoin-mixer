package com.gemini.jobcoin.models

case class Address(
  id: String,
  balance: Double,
  transactions: List[Transaction]
)
