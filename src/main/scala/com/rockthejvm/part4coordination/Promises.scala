package com.rockthejvm.part4coordination

import zio.*
import com.rockthejvm.utils.debugThread

object Promises extends ZIOAppDefault:
  val aPromise: ZIO[Any, Nothing, Promise[Throwable, Int]] = Promise.make[Throwable, Int]

  // await - block the fiber until the promise has a value
  val reader = aPromise.flatMap { promise =>
    promise.await
  }

  // succeed, fail, complete
  val writer = aPromise.flatMap { promise =>
    promise.succeed(42)
    //    promise.fail(new RuntimeException("Abc"))
    //    promise.complete(ZIO.succeed(42))
  }

  def demoPromise(): Task[Unit] = {
    // producer - consumer problem
    def consumer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed("[consumer] waiting for result...").debugThread
      mol <- promise.await
      _ <- ZIO.succeed(s"[consumer] I got a result: $mol").debugThread
    } yield ()

    def producer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed("[producer] crunching numbers...").debugThread
      _ <- ZIO.sleep(3.seconds)
      _ <- ZIO.succeed("[producer] complete.").debugThread
      mol <- ZIO.succeed(42)
      _ <- promise.succeed(mol)
    } yield ()

    for {
      promise <- Promise.make[Throwable, Int]
      _ <- consumer(promise) zipPar producer(promise)
    } yield ()
  }

  /* Promise advantages:
  - purely functional block on a fiber until you get a signal from another fiber
  - waiting on a value which may not yet be available, without thread starvation
  - inter-fiber communication
  * */

  // simulate downloading from multiple parts
  val fileParts = List("I", "love", "Sc", "ala", " with pure f", "p", "an", "d ZIO!<EOF>")

  def downloadFileWithRef(): UIO[Unit] = {
    def downloadFile(contentRef: Ref[String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          ZIO.succeed(s"got '$part'").debugThread *> ZIO.sleep(1.second) *> contentRef.update(_ + part)
        }
      )

    def notifyFileComplete(contentRef: Ref[String]): UIO[Unit] = for {
      file <- contentRef.get
      _ <- if file.endsWith("<EOF>")
      then ZIO.succeed("File download complete.").debugThread
      else ZIO.succeed("downloading...").debugThread *> ZIO.sleep(500.millis) *> notifyFileComplete(contentRef)
    } yield ()

    for {
      contentRef <- Ref.make("")
      _ <- downloadFile(contentRef) zipPar notifyFileComplete(contentRef)
    } yield ()
  }

  def downloadFileWithRefAndPromise(): Task[Unit] = {
    def downloadFile(contentRef: Ref[String], promise: Promise[Throwable, String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          for {
            _ <- ZIO.succeed(s"got '$part'").debugThread
            _ <- ZIO.sleep(1.second)
            file <- contentRef.updateAndGet(_ + part)
            _ <- if file.endsWith("<EOF>") then promise.succeed(file) else ZIO.unit
          } yield ()
        }
      )

    def notifyFileComplete(contentRef: Ref[String], promise: Promise[Throwable, String]): Task[Unit] = for {
      _ <- ZIO.succeed("downloading...").debugThread
      file <- promise.await
      _ <- ZIO.succeed(s"file download complete: $file").debugThread
    } yield ()

    for {
      contentRef <- Ref.make("")
      promise <- Promise.make[Throwable, String]
      _ <- downloadFile(contentRef, promise) zipPar notifyFileComplete(contentRef, promise)
    } yield ()
  }

  /*
  Exercises
  * */

  /* 1. Write a simulated "egg boiler" with two ZIOs that run in parallel
  - one increments a counter every 1s - Ref
  - one waits for the counter to become 10, after which it will "ring a bell"
  */
  def eggBoiler(): UIO[Unit] =
    def incrementor(promise: Promise[Throwable, Int], intRef: Ref[Int]): UIO[Unit] = for {
      int <- intRef.updateAndGet(_ + 1)
      _ <- ZIO.succeed(s"updated to $int").debugThread
      _ <- ZIO.sleep(1.second)
      isDone <- promise.isDone
      _ <- incrementor(promise, intRef).unless(isDone)
    } yield ()

    def waiter(promise: Promise[Throwable, Int], intRef: Ref[Int]): UIO[Unit] = for {
      int <- intRef.get
      _ <- if int == 10
        then ZIO.succeed("ring the bell").debugThread *> promise.succeed(int)
        else waiter(promise, intRef)
    } yield ()


    for {
      promise <- Promise.make[Throwable, Int]
      counterRef <- Ref.make(0)
      incrementorFib <- incrementor(promise, counterRef).fork
      waiterFib <- waiter(promise, counterRef).fork
      _ <- ZIO.collectAllParDiscard(List(incrementorFib.join, waiterFib.join))
      _ <- promise.await.fold(_ => ZIO.unit, _ => ZIO.unit)
      _ <- incrementorFib.interrupt
    } yield ()


  /* 2. Write a "race pair"
  - use a Promise which can hold an Either[exit for A, exit for B]
  - start a fiber for each ZIO
  - on completion (with any status), each ZIO needs to complete that Promise (hint: use a finalizer)
  - waiting on the Promise's value can be interrupted!
  - if a whole race is interrupted, interrupt the running fibers
  */
  type RacePairResult[R, E, A, B] = ZIO[
    R,
    Nothing,
    Either[
      (Exit[E, A], Fiber[E, B]),
      (Fiber[E, A], Exit[E, B])
    ]
  ]

  def racePair[R, E, A, B](zioa: => ZIO[R, E, A], ziob: => ZIO[R, E, B]): RacePairResult[R, E, A, B] = for {
    promise <- Promise.make[Nothing, Either[Exit[E, A], Exit[E, B]]]

    fiba <- zioa.fork
    fibb <- ziob.fork

    _ <- ZIO.collectAllParDiscard(List(
      fiba.join.onDoneCause(
        e => promise.succeed(Left(Exit.Failure(e))),
        v => promise.succeed(Left(Exit.Success(v)))
      ),
      fibb.join.onDoneCause(
        e => promise.succeed(Right(Exit.Failure(e))),
        v => promise.succeed(Right(Exit.Success(v)))
      ),
    ))

    promiseResult <- promise.await.onInterrupt(fiba.interrupt *> fibb.interrupt)

    result <- promiseResult match
      case Left(exit: Exit[E, A]) => ZIO.succeed(Left((exit, fibb)))
      case Right(exit: Exit[E, B]) => ZIO.succeed(Right((fiba, exit)))
  } yield result

  val demoRacePair = {
    val zioa = ZIO.sleep(1.second).as(1).onInterrupt(ZIO.succeed("first interrupted").debugThread)
    val ziob = ZIO.sleep(2.second).as(2).onInterrupt(ZIO.succeed("second interrupted").debugThread)

    val pair = racePair(zioa, ziob)

    pair.flatMap {
      case Left((exita, fibb)) => fibb.interrupt *> ZIO.succeed("first won").debugThread *> ZIO.succeed(exita).debugThread
      case Right((fiba, exitb)) => fiba.interrupt *> ZIO.succeed("second won").debugThread *> ZIO.succeed(exitb).debugThread
    }
  }


  def run = demoRacePair

