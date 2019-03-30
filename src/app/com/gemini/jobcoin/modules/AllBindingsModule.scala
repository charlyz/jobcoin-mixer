package com.gemini.jobcoin.modules

import com.gemini.jobcoin.JobcoinMixer
import com.gemini.jobcoin.http.clients.JobcoinClient
import com.google.inject.AbstractModule
import com.google.inject.Singleton

import play.api.Configuration
import play.api.Environment

@Singleton
class AllBindingsModule(
  environment: Environment,
  configuration: Configuration
) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[JobcoinClient])
    bind(classOf[JobcoinMixer])
  }

}
