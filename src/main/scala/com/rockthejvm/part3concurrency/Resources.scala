package com.rockthejvm.part3concurrency

import com.rockthejvm.part3concurrency.Resources.openFileScanner
import zio.*
import com.rockthejvm.utils.debugThread

import java.io.File
import java.util.Scanner

object Resources extends ZIOAppDefault:
  // finalizers
  def unsafeMethod(): Int = throw new RuntimeException("Not an int here for you!")

  val anAttempt = ZIO.attempt(unsafeMethod())

  // finalizers
  val attemptWithFinalizer = anAttempt.ensuring(ZIO.succeed("finalizer").debugThread)

  // multiple finalizers
  val attemptWith2Finalizers = attemptWithFinalizer.ensuring(ZIO.succeed("another finalizer").debugThread)

  // .onInterrupt, onError, onDone, onExit

  // resource lifecycle
  class Connection(url: String):
    def open() = ZIO.succeed(s"opening connection to url: $url").debugThread

    def close() = ZIO.succeed(s"closing connection $url").debugThread

  object Connection:
    def create(url: String) = ZIO.succeed(new Connection(url))

  val fetchUrl = for {
    conn <- Connection.create("rockethejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield () // resource leak

  val correctFetchUrl = for {
    conn <- Connection.create("rockethejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).ensuring(conn.close()).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield () // preventing leaks

  // tedious

  /* acquireRelease
  - acquiring cannot be interrupted
  - all finalizers are guaranteed to run
  */
  val cleanConnection = ZIO.acquireRelease(Connection.create("rockthejvm.com"))(_.close())
  val fetchWithResource = for {
    conn <- cleanConnection
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  val fetchWithScopedResource = ZIO.scoped(fetchWithResource)

  // acquireReleaseWith
  val cleanConnection_v2 = ZIO.acquireReleaseWith(
    Connection.create("rockthejvm.com")
  )(
    _.close()
  )(
    conn => conn.open() *> ZIO.sleep(300.seconds) // use
  )

  val fetchWithResource_v2 = for {
    fib <- cleanConnection_v2.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  /*
  Execrises
  1. Use the acquireRelease to open a file, print all lines, (one every 100 millis), then close the file
  // Scanner.hasNext or .nextLine, Scanner.close()
  */
  def openFileScanner(path: String): UIO[Scanner] = ZIO.succeed(new Scanner(new File(path)))

  def acquireScanner(path: String) = ZIO.acquireReleaseWith(
    openFileScanner(path)
  )(
    scanner => ZIO.succeed(println("closing scanner")) *> ZIO.succeed(scanner.close())
  )(
    printLinePeriodically
  )

  def printLinePeriodically(scanner: Scanner): UIO[Unit] = for {
    _ <- ZIO.sleep(100.millis)
    hasNext <- ZIO.succeed(scanner.hasNext())
    result <- hasNext match
      case true =>
        ZIO.succeed(println(scanner.nextLine())) *> printLinePeriodically(scanner)
      case false => ZIO.succeed(())
  } yield result

  def acquireOpenFile(path: String): UIO[Unit] = ZIO.scoped {
    for {
      _ <- acquireScanner(path)
    } yield ()
  }

  val testInterruptFileDisplay = for {
    fib <- acquireOpenFile("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala").fork
    _ <- ZIO.sleep(2.seconds) *> fib.interrupt
  } yield ()

  // acquireRelease vs acquireReleaseWith
  def connFromConfig(path: String): UIO[Unit] =
    ZIO.acquireReleaseWith(openFileScanner(path))(scanner => ZIO.succeed("closing file").debugThread *> ZIO.succeed(scanner.close())) {
      scanner =>
        ZIO.acquireReleaseWith(Connection.create(scanner.nextLine()))(_.close()) {
          conn =>
            conn.open() *> ZIO.never
        }
    }

  // nested resources
  def connFromConfig_v2(path: String): UIO[Unit] = ZIO.scoped {
    for {
      scanner <- ZIO.acquireRelease(openFileScanner(path))(scanner => ZIO.succeed("closing file").debugThread *> ZIO.succeed(scanner.close()))
      conn <- ZIO.acquireRelease(Connection.create(scanner.nextLine()))(_.close())
      _ <- conn.open() *> ZIO.never
    } yield ()
  }

  def run = connFromConfig_v2("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala")
