package com.gemini.jobcoin

import java.util.concurrent.Callable

import scala.collection.mutable.{Map => MMap}
import scala.collection.mutable.{Set => MSet}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime

import com.gemini.jobcoin.Implicits.RichDouble
import com.gemini.jobcoin.http.clients.JobcoinClient
import com.gemini.jobcoin.models.Address
import com.gemini.jobcoin.models.Transaction

import akka.actor.ActorSystem
import akka.actor.Scheduler
import akka.pattern.Patterns.after
import javax.inject.Inject
import javax.inject.Singleton
import play.Logger

class JobcoinMixer @Inject()(
  config: JobcoinConfiguration,
  jobcoinClient: JobcoinClient,
  actorSystem: ActorSystem
) {

  val transactionTimestampThreshold = DateTime.now
  val archives = MSet[Transaction]()
  val transactionToRemainingRefund = MMap[Transaction, Double]()

  @volatile var stopping: Boolean = false
  val scheduler: Scheduler = actorSystem.scheduler

  def stop() = stopping = true

  def scheduleNextCheck(in: FiniteDuration, retries: Int) = {
    after(
      in,
      scheduler,
      actorSystem.dispatcher,
      new Callable[Future[Unit]]() {
        def call(): Future[Unit] = checkIncomingTransactionsToMix(retries)
      }
    )
  }

  def checkIncomingTransactionsToMix(retries: Int): Future[Unit] = {
    Logger.info("Checking for new transactions to mix...")

    // Step 1: We get all the mixer wallets.
    Future
      .traverse(config.Mixer.AddressIds) {
        case addressId =>
          jobcoinClient.getAddress(addressId)
      }
      .flatMap { mixerAddresses =>
        // Step 2: We look for new incoming transations.
        val newIncomingTransactionsToMix = mixerAddresses.flatMap {
          case mixerAddress =>
            mixerAddress.transactions
              .filter { transaction =>
                if (transaction.timestamp.isBefore(transactionTimestampThreshold) ||
                    archives.contains(transaction)) {
                  false
                } else {
                  transaction.fromAddressIdOpt match {
                    case Some(fromAddressId) => !config.Mixer.AddressIds.contains(fromAddressId)
                    case _ => false
                  }
                }
              }
        }

        Logger.info(s"Found ${newIncomingTransactionsToMix.size} transactions to mix.")

        // Step 3: We rebalance and send the coins to the hidden wallets, once at a time.
        FutureHelpers
          .executeSequentially(newIncomingTransactionsToMix, List()) {
            case transactionToMix =>
              mixTransaction(transactionToMix)
          }
      }
      .flatMap { _ =>
        Logger.info("Done checking for new incoming transactions. Scheduling next check...")

        // Step 4: Everything went fine, let's schedule the next polling.
        if (stopping) {
          Future.successful(())
        } else {
          scheduleNextCheck(in = config.Mixer.CheckInterval, retries = 0)
        }
      }
      .recoverWith {
        case e =>
          // Something somewhere above did go wrong.
          // Let's attempt a new retry in a few.
          val retryInterval = Try {
            config.Mixer.MinimumRetryInterval
              .mul(Math.pow(config.Mixer.ExponentialBackoff, retries).toLong)
              .min(config.Mixer.MaximumRetryInterval)
          } match {
            case Success(computedRetryInterval) => computedRetryInterval
            case Failure(_) => config.Mixer.MaximumRetryInterval
          }

          Logger.error(
            "An error occured while trying to mix one or multiple transaction. " +
              s"Retrying in ${retryInterval.toSeconds}s after $retries retries...",
            e
          )

          val newRetriesCount = retries + 1

          scheduleNextCheck(in = retryInterval, newRetriesCount)
      }
  }

  def mixTransaction(transactionToMix: Transaction): Future[Unit] = {
    rebalance()
      .flatMap { _ =>
        Future
          .traverse(config.Mixer.AddressIds) {
            case addressId =>
              jobcoinClient.getAddress(addressId)
          }
          .flatMap { mixerAddresses =>
            val amountToMixAfterFee = transactionToRemainingRefund
              .get(transactionToMix)
              .getOrElse(
                transactionToMix.amount - (transactionToMix.amount * config.Mixer.Fee)
              )
              .setScale(5)

            mixTransactionImpl(
              transactionToMix,
              remainingMixerAddresses = mixerAddresses,
              remainingAmount = amountToMixAfterFee
            )
          }
          .map { _ =>
            archives.add(transactionToMix)
          }
      }
  }

  private def mixTransactionImpl(
    transactionToMix: Transaction,
    remainingMixerAddresses: List[Address],
    remainingAmount: Double
  ): Future[Unit] = {
    val random = new Random()
    // Step 1: We select a random mixer wallet.
    val randomMixerAddress = remainingMixerAddresses(random.nextInt(remainingMixerAddresses.size))
    // Step 2: We generate a random amount except if it is less than 10%
    // left to distribute.
    val randomIncrementalRemainingAmount = if (remainingAmount < transactionToMix.amount * 0.1) {
      remainingAmount
    } else {
      remainingAmount * (0.01 + (1 - 0.01) * random.nextDouble())
    }

    // Step 3: We make sure that the mixer wallet has enough fund.
    val incrementalRemainingAmount = randomMixerAddress.balance.min(randomIncrementalRemainingAmount).setScale(5)
    val randomMixerAddressWithNewBalance = randomMixerAddress.copy(
      balance = randomMixerAddress.balance - incrementalRemainingAmount
    )

    val newRemainingMixerAddresses = remainingMixerAddresses.flatMap {
      case address if address == randomMixerAddress =>
        if (address.balance == 0) {
          None
        } else {
          Some(randomMixerAddressWithNewBalance)
        }
      case address => Some(address)
    }

    val transactionFromAddressId = transactionToMix.fromAddressIdOpt match {
      case Some(fromAddressId) => fromAddressId
      case _ => throw new Exception("Missing from address.")
    }

    // Step 4: We select a random hidden wallet.
    val hiddenNumber = 1 + random.nextInt(config.Mixer.HiddenWalletsCount)
    val hiddenWalletId = s"$transactionFromAddressId-Hidden$hiddenNumber"

    val transactionCreationFuture = if (incrementalRemainingAmount == 0) {
      // Step 5.a It is possible that the mixer wallet had a very low balance. We skip this iteration.
      Future.successful(())
    } else {
      // Step 5.b: We transfer the incremental amount.
      jobcoinClient
        .createTransaction(
          Transaction(
            amount = incrementalRemainingAmount,
            toAddressId = hiddenWalletId,
            fromAddressIdOpt = Some(randomMixerAddress.id),
            timestamp = DateTime.now
          )
        )
    }

    transactionCreationFuture.flatMap { _ =>
      val newRemainingAmount = (remainingAmount - incrementalRemainingAmount).setScale(5)

      Logger.info(
        s"$incrementalRemainingAmount has been sent from ${randomMixerAddress.id} to $hiddenWalletId. " +
          s"Remaining amount to mix: $newRemainingAmount out of ${transactionToMix.amount}"
      )

      // Step 6: We keep track of what has been refunded in case of future error.
      transactionToRemainingRefund(transactionToMix) = newRemainingAmount

      if (newRemainingAmount == 0) {
        // Step 7.a: No more fund to distribute, we're good.
        Future.successful(())
      } else {
        // Step 7.b: We still have fund to distribute.
        mixTransactionImpl(
          transactionToMix,
          newRemainingMixerAddresses,
          newRemainingAmount
        )
      }
    }
  }

  def rebalance() = {
    Logger.info("Rebalancing...")
    Future
      .traverse(config.Mixer.AddressIds) {
        case addressId =>
          jobcoinClient.getAddress(addressId)
      }
      .flatMap { mixerAddresses =>
        val balances = mixerAddresses.map(_.balance)
        val average = balances.sum / balances.size
        rebalanceImpl(mixerAddresses, rebalancingAttemptsLeft = config.Mixer.MaxRebalancingAttempts, average)
      }
      .map { _ =>
        Logger.info("Done rebalancing.")
      }

  }

  def isMixerUnbalanced(addresses: List[Address], average: Double) = {
    addresses.exists {
      case address =>
        val difference = (address.balance - average).abs
        difference / average > config.Mixer.RebalancingVariance
    }
  }

  private def rebalanceImpl(
    addresses: List[Address],
    rebalancingAttemptsLeft: Int,
    average: Double
  ): Future[Unit] = {
    if (isMixerUnbalanced(addresses, average) && rebalancingAttemptsLeft > 0) {
      addresses.headOption match {
        case Some(firstAddress) =>
          val (addressWithLowestBalance, addressWithHighestBalance) = addresses.foldLeft((firstAddress, firstAddress)) {
            case ((currentAddressWithLowestBalance, currentAddressWithHighestBalance), nextAddress) =>
              val newAddressWithLowestAddress = if (currentAddressWithLowestBalance.balance > nextAddress.balance) {
                nextAddress
              } else {
                currentAddressWithLowestBalance
              }

              val newAddressWithHighestAddress = if (currentAddressWithHighestBalance.balance < nextAddress.balance) {
                nextAddress
              } else {
                currentAddressWithHighestBalance
              }
              (newAddressWithLowestAddress, newAddressWithHighestAddress)
          }

          val possibleAmountToSend = addressWithHighestBalance.balance - average
          val possibleAmountToReceive = average - addressWithLowestBalance.balance
          val amountToBalance = possibleAmountToSend.min(possibleAmountToReceive)

          val transactionCreationFuture = if (amountToBalance == 0) {
            Future.successful(())
          } else {
            Logger.info(
              s"Sending $amountToBalance from ${addressWithHighestBalance.id} " +
                s"(${addressWithHighestBalance.balance}) to ${addressWithLowestBalance.id} " +
                s"(${addressWithLowestBalance.balance})."
            )

            jobcoinClient
              .createTransaction(
                Transaction(
                  timestamp = DateTime.now,
                  amount = amountToBalance,
                  toAddressId = addressWithLowestBalance.id,
                  fromAddressIdOpt = Some(addressWithHighestBalance.id)
                )
              )
          }

          transactionCreationFuture.flatMap { _ =>
            val addressesWithUpdatedBalances = addresses.map {
              case address if address == addressWithLowestBalance =>
                addressWithLowestBalance.copy(balance = addressWithLowestBalance.balance + amountToBalance)
              case address if address == addressWithHighestBalance =>
                addressWithHighestBalance.copy(balance = addressWithHighestBalance.balance - amountToBalance)
              case address => address
            }

            rebalanceImpl(
              addressesWithUpdatedBalances,
              rebalancingAttemptsLeft - 1,
              average
            )
          }
        case _ => Future.failed(new Exception("The number of mixer addresses needs to be at least 1."))
      }
    } else {
      if (rebalancingAttemptsLeft > 0) {
        Logger.info("Mixer addresses are balanced.")
      } else {
        Logger.info("Mixer addresses are not balanced but there is no more attempt left.")
      }

      Future.successful(())
    }

  }

}
