package com.rockthejvm.part2effects

import zio.*

import java.io.IOException
import java.net.NoRouteToHostException
import scala.util.Try

object ZIOErrorHandling extends ZIOAppDefault {
  // ZIOs can fail
  val aFailedZIO = ZIO.fail("Something went wrong")
  val failedWithThrowable = ZIO.fail(new RuntimeException("Boom!"))
  val failedWithDescription = failedWithThrowable.mapError(_.getMessage)

  // attempt: run an effect that might throw an exception
  val badZIO = ZIO.succeed {
    println("Trying something")
    val string: String = null
    string.length
  }

  val betterZIO = ZIO.attempt {
    println("Trying something")
    val string: String = null
    string.length
  }

  val anAttempt = betterZIO

  // effectfully catch errors
  val catchError = anAttempt.catchAll(e => ZIO.succeed("Returning a different value because $e"))
  val catchSelectiveErrors = anAttempt.catchSome {
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exceptions: $e")
    case e => ZIO.succeed(s"Ignoring everything else: $e")
  }

  // chain effects
  val aBetterAttempt = anAttempt.orElse(ZIO.succeed(543))

  // fold: handle both success and failure
  val handleBoth: ZIO[Any, Nothing, String] = anAttempt.fold(
    ex => s"Something bad happened: $ex",
    value => s"Length of the string was $value"
  )

  // effectful fold: foldZIO
  val handleBoth_v2 = anAttempt.foldZIO(
    ex => ZIO.succeed(s"Something bad happened: $ex"),
    value => ZIO.succeed(s"Length of the string was $value")
  )

  /*
  Conversions between Option/Try/Either to ZIO
  */
  val tryToZIO = ZIO.fromTry(Try(42 / 0)) // can fail with Throwable

  // Either -> ZIO
  val anEither: Either[Int, String] = Right("Success!")
  val anEitherToZIO: ZIO[Any, Int, String] = ZIO.fromEither(anEither)
  // ZIO -> ZIO with Either as the value channel
  val eitherZIO = anAttempt.either
  // reverse
  val anAttempt_v2 = eitherZIO.absolve

  // Option -> ZIO
  val anOption: ZIO[Any, Option[Nothing], Int] = ZIO.fromOption(Some(42))

  /*
  Exercise: implement a version of
  - [x] fromTry
  - [x] fromOption
  - [x] fromEither
  - [x] either
  - [x] absolve

  using fold and foldZIO
  * */

  def fromTry[A](t: Try[A]): Task[A] = t.fold(ZIO.fail, ZIO.succeed)

  def fromOption[A](o: Option[A]): Task[A] = o.fold
    (ZIO.fail(new Exception("Should be empty")))
    (v => ZIO.succeed(v))

  def fromEither[E, A](et: Either[E, A]): IO[E, A] = et.fold(ZIO.fail, ZIO.succeed)

  def either[R, E, A](zio: ZIO[R, E, A]): URIO[R, Either[E, A]] = zio.fold(e => Left(e), v => Right(v))

  def absolve[R, E, A](et: Either[E, A]): ZIO[R, E, A] = et.fold(ZIO.fail, ZIO.succeed)

  /*
  Errors = failures present in the ZIO type signature ("checked errors")
  Defects = failures that are unrecoverable, unforeseen, NOT present in the ZIO type signature

  ZIO[R, E, A] can finish with Exit[E, A]
  - Success[A] containing a value
  - Cause[E]
    - Fail[E]
    - Die[t: Throwable) which was unforeseen
  * */
  val divisionByZero: UIO[Int] = ZIO.succeed(1 / 0)

  val failedInt: ZIO[Any, String, Int] = ZIO.fail("I failed!")
  val failureCauseExposed: ZIO[Any, Cause[String], Int] = failedInt.sandbox
  val failureCauseHidden: ZIO[Any, String, Int] = failureCauseExposed.unsandbox
  // fold with cause
  val foldedWithCause = failedInt.foldCause(
    cause => s"This failed with ${cause.defects}",
    value => s"this succeeded with $value"
  )
  val foldedWithCause_v2 = failedInt.foldCauseZIO(
    cause => ZIO.fail(s"This failed with ${cause.defects}"),
    value => ZIO.succeed(s"this succeeded with $value")
  )

  /*
  Good practice:
  - at lower level, your "errors" should be treated
  - at a higher level,  you should hide "errors" and assume they are unrecoverable
  * */
  def callHttpEndpoint(url: String): ZIO[Any, IOException, String] =
    ZIO.fail(new IOException("No Internet, dummy!"))

  val endpointCallWithDefects: ZIO[Any, Nothing, String] =
    callHttpEndpoint("google.com").orDie // all errors are now defects

  // refining the error channel
  def callHttpEndpointWideError(url: String): ZIO[Any, Exception, String] =
    ZIO.fail(new IOException("No Internet!"))

  def callHttpEndpoint_v2(url: String): ZIO[Any, IOException, String] =
    callHttpEndpointWideError(url).refineOrDie[IOException] {
      case e: IOException => e
      case _: NoRouteToHostException => new IOException(s"No route to host to $url, can't fetch page")
    }

  // reverse: turn defects into the error channel
  val endpointCallWithError = endpointCallWithDefects.unrefine {
    case e => e.getMessage
  }

  /*
  Combine effects with different errors
  * */
  case class IndexError(message: String)
  case class DbError(message: String)
  val callApi: ZIO[Any, IndexError, String] = ZIO.succeed("page: <html></html>")
  val queryDb: ZIO[Any, DbError, Int] = ZIO.succeed(1)
//  val combined: ZIO[Any, (IndexError | DbError, IndexError | DbError), Int] = for {
//    page <- callApi
//    rowsAffected <- queryDb
//  } yield (page, rowsAffected) // lost type safety
  /*
  Solutions:
  - design error model
  - use Scala 3 union types
  - .mapError to some common error type
  * */


  /*
  Exercises
  * */
  // 1. Make this effect fail with a TYPED error
  val aBadFailure: IO[RuntimeException, Int] = ZIO.succeed[Int](throw new RuntimeException("this is bad!"))

  // 2. Transform ZIO into another ZIO with narrower exception type
  def ioException[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] = zio.refineOrDie[IOException] {
    case e: IOException => e
  }

  // 3.
  def left[R, E, A, B](zio: ZIO[R, E, Either[A, B]]): ZIO[R, Either[E, A], B] = zio.foldZIO(
    e => ZIO.fail(Left(e)),
    value => value match
      case Left(e) => ZIO.fail(Right(e))
      case Right(v) => ZIO.succeed(v)
  )

  // 4.
  val database = Map(
    "Daniel" -> 123,
    "Alice" -> 789
  )
  case class QueryError(reason: String)
  case class UserNotFoundError(id: String)
  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if (userId != userId.toLowerCase()) then
      ZIO.fail(QueryError("User ID format is invalid"))
    else
      ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))

  // surface out all the failed cases of this API
  def betterLookupProfileApi(id: String): ZIO[Any, QueryError | UserNotFoundError, UserProfile] =
    lookupProfile(id).foldZIO(
      e => ZIO.fail(e),
      value => value match
        case None => ZIO.fail(UserNotFoundError(id))
        case Some(v) => ZIO.succeed(v)
    )


  override def run = for {
    _ <- ZIO.succeed(println("start"))
  } yield ()
}
