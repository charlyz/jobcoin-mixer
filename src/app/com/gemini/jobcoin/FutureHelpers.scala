package com.gemini.jobcoin

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object FutureHelpers {

  // Execute a list of futures one by one. The
  // native function Future.traverse uses all
  // the available threads to go through the
  // list.
  def executeSequentially[A, B](
    in: List[A],
    results: List[B] = List()
  )(
    fn: A => Future[B]
  )(
    implicit executor: ExecutionContext
  ): Future[List[B]] = {
    in match {
      case nextArgument :: restOfArguments =>
        Try(fn(nextArgument)) match {
          case Success(resultFuture) =>
            resultFuture.flatMap { nextResult =>
              executeSequentially(restOfArguments, results ++ List(nextResult))(fn)
            }
          case Failure(e) => Future.failed(e)
        }
      case Nil => Future.successful(results)
    }
  }

}
