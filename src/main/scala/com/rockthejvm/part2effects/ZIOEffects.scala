package com.rockthejvm.part2effects

import zio.*

object ZIOEffects {
  //  case class MyZIO[-R, +E, +A](unsafeRun: R => Either[E, A]) {
  //    def map[B](f: A => B): MyZIO[R, E, B] =
  //      MyZIO(r => unsafeRun(r) match {
  //        case Left(e) => Left(e)
  //        case Right(v) => Right(f(v))
  //      })
  //
  //    def flatMap[R1 <: R, E1 >: E, B](f: A => MyZIO[R1, E1, B]): MyZIO[R1, E1, B] =
  //      MyZIO(r => unsafeRun(r) match {
  //        case Left(e) => Left(e)
  //        case Right(v) => f(v).unsafeRun(r)
  //      })
  //  }


  // success
  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)

  // failure
  val aFailure: ZIO[Any, String, Nothing] = ZIO.fail("Something went wrong")

  // suspension / delay
  val aSuspendedZIO: ZIO[Any, Throwable, Int] = ZIO.suspend(meaningOfLife)

  // map + flatMap
  val improvedMOL = meaningOfLife.map(_ + 2)
  val printingMOL = meaningOfLife.flatMap(mol => ZIO.succeed(println(mol)))
  // for comprehension
  val smallProgram = for {
    _ <- ZIO.succeed(println("What's your name?"))
    name <- ZIO.succeed(scala.io.StdIn.readLine())
    _ <- ZIO.succeed(println(s"Welcome to ZIO, $name"))
  } yield ()

  // A LOT of combinators and transformers
  // zip
  val anotherMOL = ZIO.succeed(100)
  val tupledZIO = meaningOfLife.zip(anotherMOL)
  // zipWith
  val combinedZIO = meaningOfLife.zipWith(anotherMOL)(_ * _)

  // Type aliases
  // UIO = ZIO[Any, Nothing, A]
  val aUIO: UIO[Int] = ZIO.succeed(88)

  // URIO[R, A] = ZIO[R, Nothing, A] - cannot fail
  val aURIO: URIO[Int, Int] = ZIO.succeed(67)

  // RIO[R, A] = ZIO[R, Throwable, A] - can fail with Throwable
  val anRIO: RIO[Int, Int] = ZIO.succeed(99)
  val aFailedRIO: RIO[Int, Int] = ZIO.fail(new RuntimeException("ZIO failed"))

  // Task = ZIO[Any, Throwable, A] - no requirements, can fail with a Throwable, produces A
  val aSuccessfulTask: Task[Int] = ZIO.succeed(77)
  val aFailedTask: Task[Int] = ZIO.fail(new RuntimeException("Something bad"))

  // IO[E, A] = ZIO[Any, E, A] - no requirements
  val aSuccessfulIO: IO[String, Int] = ZIO.succeed(43123)
  val aFailedIO: IO[String, Int] = ZIO.fail("Something happened")

  /*
  Exercises
  1. Sequence two ZIOs and take the value of the last one
  Built-in operator: *>
  */
  def sequenceTakeLast[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, B] =
    zioa.flatMap(_ => ziob.map(b => b))

  /*
  2. Sequence two ZIOs and take the value of the first one
  Built-in operator: <*
  * */
  def sequenceTakeFirst[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, A] =
    zioa.flatMap(a => ziob.map(_ => a))

  /*
  3. Run ZIO forever
  */
  def runForever[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = zio.flatMap(_ => runForever(zio))

  val endlessLoop = runForever {
    ZIO.succeed {
      println("running ...")
      Thread.sleep(1000)
    }
  }

  /*
  4. Convert value of a ZIO to something else
  Built-in method: ZIO.as
  */
  def convert[R, E, A, B](zio: ZIO[R, E, A], value: B): ZIO[R, E, B] =
    sequenceTakeLast(zio, ZIO.succeed(value))

  /*
  5. Discard value of a ZIO to Unit
  * */
  def asUnit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] = sequenceTakeLast(zio, ZIO.succeed(()))

  /*
  6. recursion
  */
//  def sum(n: Int): Int =
//    if n == 0 then 0
//    else n + sum(n - 1) // will crash

  def sumZIO(n: Int): UIO[Int] =
    if n == 0 then ZIO.succeed(0)
    else for {
      current <- ZIO.succeed(n)
      prevSum <- sumZIO(n - 1)
    } yield current + prevSum

  /*
  7. - fibonacci
  hint: use ZIO.suspend or suspendSucceed
  */
  def fiboZIO(n: Int): UIO[BigInt] =
    if n <= 1 then ZIO.succeed(n)
    else for {
      a <- ZIO.suspendSucceed(fiboZIO(n - 1))
      b <- ZIO.suspendSucceed(fiboZIO(n - 2))
    } yield a + b
//    else fiboZIO(n - 1).flatMap(a => fiboZIO(n - 2).map(b => a + b))

  def main(args: Array[String]): Unit = {
    val runtime = Runtime.default

    given trace: Trace = Trace.empty

    val run = Unsafe.unsafe { () => {
//      runtime.unsafe.run(endlessLoop)
      runtime.unsafe.run({
        for {
          _ <- ZIO.succeed(println("hello"))
          sum <- sumZIO(20000)
          _ <- ZIO.succeed(println(s"sum $sum"))
          fib <- fiboZIO(20)
          _ <- ZIO.succeed(println(s"fib $fib"))
        } yield ()
      })
    }
    }

    run()
  }
}
