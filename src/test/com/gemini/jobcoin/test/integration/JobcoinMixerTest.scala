package com.gemini.jobcoin.test.integration

import java.io.File

import scala.annotation.implicitNotFound
import scala.collection.mutable.{Set => MSet}
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.existentials
import scala.language.implicitConversions

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{atLeast => mAtLeast}
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.{when => MWhen}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.GivenWhenThen
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.Matchers.convertToStringShouldWrapper
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

import com.gemini.jobcoin.JobcoinApplicationLoader
import com.gemini.jobcoin.JobcoinConfiguration
import com.gemini.jobcoin.JobcoinMixer
import com.gemini.jobcoin.http.clients.JobcoinClient
import com.gemini.jobcoin.models.Address
import com.gemini.jobcoin.models.Transaction
import com.gemini.jobcoin.modules.StartAndStopModule

import javax.inject.Singleton
import play.api.Application
import play.api.ApplicationLoader
import play.api.Environment
import play.api.Mode
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule.fromPlayBinding

class DailyFxRatesSnapshotSchedulerTest
  extends PlaySpec
  with MockitoSugar
  with BeforeAndAfterEach
  with GivenWhenThen
  with BeforeAndAfterAll {

  "scheduleNextCheck" should {
    "eventually mix coins" when {
      "a new incoming transation is detected" in {
        // We mock two mixer wallets without any transactions.
        MWhen(jobcoinClient.getAddress("Mixer1"))
          .thenReturn(Future.successful(Address(id = "Mixer1", balance = 10, transactions = List())))

        MWhen(jobcoinClient.getAddress("Mixer2"))
          .thenReturn(Future.successful(Address(id = "Mixer2", balance = 10, transactions = List())))

        var totalAmountMixed = 0.0
        var hiddenWallets = MSet[String]()

        // We mock jobcoinClient.createTransaction to get the amount of
        // coins sent to the hidden wallets. With more time, we would want
        // to correctly mock the Jobcoin API or embed it.
        doAnswer(
          new Answer[Future[Unit]]() {
            def answer(invocation: InvocationOnMock): Future[Unit] = {
              val transaction = invocation.getArgument[Transaction](0)
              totalAmountMixed = totalAmountMixed + transaction.amount
              hiddenWallets.add(transaction.toAddressId)
              Future.successful(())
            }
          }
        ).when(jobcoinClient)
          .createTransaction(any[Transaction])

        // Here we go, we start the polling.
        jobcoinMixer.scheduleNextCheck(in = config.Mixer.CheckInterval, retries = 0)

        Thread.sleep(2000)

        // Less than 5 seconds elapsed so polling yet.
        totalAmountMixed shouldBe 0.0
        verify(jobcoinClient, times(0)).getAddress("Mixer1")
        verify(jobcoinClient, times(0)).getAddress("Mixer2")

        Thread.sleep(5000)

        // By now, we should have polled the transactions at least once.
        totalAmountMixed shouldBe 0.0
        verify(jobcoinClient, mAtLeast(1)).getAddress("Mixer1")
        verify(jobcoinClient, mAtLeast(1)).getAddress("Mixer2")

        // Let's create a new incoming transaction...
        val newTransaction = Transaction(
          timestamp = DateTime.now,
          amount = 10,
          fromAddressIdOpt = Some("Robert"),
          toAddressId = "Mixer1"
        )

        // ... also rebalance the mixer wallets ourselves (15-15 instead of 20-10).
        MWhen(jobcoinClient.getAddress("Mixer1"))
          .thenReturn(Future.successful(Address(id = "Mixer1", balance = 15, transactions = List(newTransaction))))

        MWhen(jobcoinClient.getAddress("Mixer2"))
          .thenReturn(Future.successful(Address(id = "Mixer2", balance = 15, transactions = List())))

        Thread.sleep(8000)

        // After waiting a while, let's check that we tried to move around 9 coins
        // in two hidden wallets.
        totalAmountMixed shouldBe 9.0 +- 0.0001
        if (hiddenWallets.size == 2) {
          hiddenWallets should contain("Robert-Hidden1")
          hiddenWallets should contain("Robert-Hidden2")
        }
      }
    }
  }

  // We load app-test.conf
  private val context = ApplicationLoader.createContext(
    new Environment(new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
  )

  var app: Application = _
  var jobcoinClient: JobcoinClient = _
  var jobcoinMixer: JobcoinMixer = _
  var config: JobcoinConfiguration = _

  override def beforeEach(): Unit = {
    jobcoinClient = mock[JobcoinClient]

    app = new JobcoinApplicationLoader()
      .builder(context)
      .overrides(bind[JobcoinClient].toInstance(jobcoinClient))
      .disable(classOf[StartAndStopModule])
      .build()

    jobcoinMixer = app.injector.instanceOf(classOf[JobcoinMixer])
    config = app.injector.instanceOf(classOf[JobcoinConfiguration])
  }

  override def afterEach(): Unit =
    Await.result(app.stop(), 10.seconds)

}
