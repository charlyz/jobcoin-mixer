package com.gemini.jobcoin.modules

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.gemini.jobcoin.JobcoinConfiguration
import com.gemini.jobcoin.JobcoinMixer
import com.google.inject.AbstractModule
import com.google.inject.Singleton

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject
import play.Logger
import play.api.inject.ApplicationLifecycle
// One the many modules configuring Guice to know
// what can be injected or not. As reminder, the Guice
// instance is created in ApplicationLoader.
// Play knows where to look for modules by looking at the
// configuration list `play.modules`.
class StartAndStopModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[StartAndStopHook]).asEagerSingleton()
}

// We use a Guice component that we instantiate
// eagerly to start any asynchronous service.
@Singleton
class StartAndStopHook @Inject()(
  config: JobcoinConfiguration,
  lifecycle: ApplicationLifecycle,
  jobcoinMixer: JobcoinMixer,
  implicit val actorSystem: ActorSystem,
  implicit val ec: ExecutionContext,
  implicit val mat: Materializer
) {
  jobcoinMixer
    .checkIncomingTransactionsToMix(retries = 0)
    .recover {
      case e =>
        Logger.error("An unexpected error happened. Stopping the application...", e)
        lifecycle.stop()
    }

  Logger.info(
    s"""
     |
     |--------------------------------------------------------------------------------------------
     |
     | Jobcoin Mixer
     | 
     | 
     | Please send the coins you wish to mix to one of these addresses:
       ${config.Mixer.AddressIds.map(id => s"|  - $id").mkString("\n")}
     |
     |
     | Your mixed coins will then be available in the three following wallets:
     |  - YourAddress-Hidden1
     |  - YourAddress-Hidden2
     |  - YourAddress-Hidden3
     | 
     | Fee: 10%
     |
     |--------------------------------------------------------------------------------------------
     |
     """.stripMargin
  )

  lifecycle.addStopHook { () =>
    jobcoinMixer.stop()
    Future.successful(())
  }
}
