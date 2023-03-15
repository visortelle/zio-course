package com.rockthejvm.part3concurrency

import zio.*
import com.rockthejvm.utils.debugThread

object Interruptions extends ZIOAppDefault {
  val zioWithTime = (
    ZIO.succeed("starting computation").debugThread *>
      ZIO.sleep(5.seconds) *>
      ZIO.succeed(42).debugThread
    ).onInterrupt(ZIO.succeed("I was interrupted!").debugThread)
  // onInterrupt, onDone

  val interruption = for {
    fib <- zioWithTime.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting").debugThread *> fib.interrupt /* <-- this is an effect, blocks the calling fiber until the interrupted fiber is done/interrupted */
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  val interruption_v2 = for {
    fib <- zioWithTime.fork
    //    _ <- (ZIO.sleep(1.second) *> ZIO.succeed("Interrupting").debugThread *> fib.interrupt).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting").debugThread *> fib.interruptFork // same as line above
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  /*
  Automatic interruption
  * */
  // outliving a parent fiber
  val parentEffect = ZIO.succeed("spawning fiber").debugThread *>
    //    zioWithTime.fork *> // spawning child fiber
    zioWithTime.forkDaemon *> // this fiber will now be a child of the MAIN fiber
    ZIO.sleep(1.second) *>
    ZIO.succeed("parent successful").debugThread // done here

  val testOutlivingParent = for {
    parentEffectFib <- parentEffect.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()
  // child fibers will be (automatically) interrupted if the parent fiber is completed (for any reason)

  // racing
  val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow").debugThread)
    .onInterrupt(ZIO.succeed("[slow] interrupted").debugThread)
  val fastEffect = (ZIO.sleep(1.seconds) *> ZIO.succeed("fast").debugThread)
    .onInterrupt(ZIO.succeed("[fast] interrupted").debugThread)
  val aRace = slowEffect.race(fastEffect)
  val testRace = aRace.fork *> ZIO.sleep(3.seconds)

  /*
  Exercises
  * */
  /* 1. implement timeout function
      - if zio is successful before timeout, return a successful effect
      - if zio is fails with an error, return a failed effect
      - if zio takes longer than timeout, interrupt the effect
  */
  def timeout[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, A] = for {
    zioFib <- zio.fork
    _ <- ZIO.sleep(time).onExit(_ => ZIO.succeed(zioFib.interruptFork)).fork
    result <- zioFib.join
  } yield result

  /* 2. timeout v2 - same a prev, but:
      - it zio takes longer then timeout, success with None
  // hint: foldCauseZIO
  // Cause.isInterrupted also might be useful
  * */
  def timeout_v2[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, Option[A]] = for {
    zioFib <- zio.fork
    _ <- ZIO.sleep(time).onExit(_ => ZIO.succeed(zioFib.interruptFork)).fork
    result <- zioFib.join.foldCauseZIO(
      cause => {
        if cause.isInterrupted
        then ZIO.succeed(None)
        else ZIO.failCause(cause)
      },
      value => ZIO.succeed(Some(value))
    )
  } yield result


  def run = testRace
}
