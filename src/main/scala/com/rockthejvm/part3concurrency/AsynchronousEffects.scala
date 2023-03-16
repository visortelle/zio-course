package com.rockthejvm.part3concurrency

import zio.*
import com.rockthejvm.utils.debugThread

import java.util.concurrent.{Callable, ExecutorService, Executors}
import scala.concurrent.ExecutionContext

object AsynchronousEffects extends ZIOAppDefault:
  // CALLBACK-based
  // asynchronous
  object LoginService:
    case class AuthError(message: String)

    case class UserProfile(email: String, name: String)

    // thread pool
    val executor = Executors.newFixedThreadPool(8)

    // "database"
    val passwd = Map(
      "a@b.com" -> "123"
    )

    // the profile data
    val database = Map(
      "a@b.com" -> "John"
    )

    def login(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit) =
      executor.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting login for $email")
        passwd.get(email) match {
          case Some(`password`) =>
            onSuccess(UserProfile(email, database(email)))
          case Some(_) =>
            onFailure(AuthError("Incorrect password"))
          case None =>
            onFailure(AuthError("User with this email doesn't exist."))
        }
      }

  def loginAsZIO(id: String, pw: String): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] =
    ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // callback object created by ZIO
      LoginService.login(id, pw)(
        profile => cb(ZIO.succeed(profile)), // notify the ZIO fiber to complete the ZIO with a success
        error => cb(ZIO.fail(error)) // same, with a failure
      )
    }

  val loginProgram = for {
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZIO(email, pass).debugThread
    _ <- Console.printLine(s"Welcome to Rock the JVM, ${profile.name}")
  } yield ()

  /*
  Exercises
  */
  // 1. lift a computation running on some (external) thread to a ZIO
  // hint: invoke the cb when the computation is complete
  // hint 2: don't wrap the computation into a ZIO
  def externalToZIO[A](computation: () => A)(executor: ExecutorService): Task[A] = ZIO.async[Any, Throwable, A] { cb =>
    try {
      val result = executor.submit(new Callable[A]() {
        override def call(): A = computation()
      }).get()
      cb(ZIO.succeed(result))
    } catch {
      case e => cb(ZIO.fail(e))
    }
  }

  val demoExternalToZIO = {
    val executor = Executors.newFixedThreadPool(8)
    val zio: Task[Int] = externalToZIO { () =>
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    }(executor)

    zio.debugThread.unit
  }

  // 2. lift a Future to ZIO
  // hint: invoke cb when the Future completes

  import scala.concurrent.Future
  import concurrent.ExecutionContext.Implicits.global
  def futureToZIO[A](future: => Future[A])(implicit ec: ExecutionContext): Task[A] = ZIO.async[Any, Throwable, A] { cb =>
    future.onComplete {
      case scala.util.Success(value) => cb(ZIO.succeed(value))
      case scala.util.Failure(e) => cb(ZIO.fail(e))
    }
  }
//  val zioFromFuture = ZIO.fromFuture() // same

  val demoFutureToZIO = {
    val executor = Executors.newFixedThreadPool(8)
    implicit val ec = ExecutionContext.fromExecutorService(executor)
    val mol = futureToZIO(Future {
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    })

    mol.debugThread.unit
  }

  // 3. implement a never-ending ZIO
  // cb
  def neverEndingZIO[A]: UIO[A] = ZIO.async[Any, Nothing, A] { cb => }
  val never = ZIO.never // same

  def run = demoFutureToZIO
