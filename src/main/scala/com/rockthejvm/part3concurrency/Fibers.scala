package com.rockthejvm.part3concurrency

import zio.{Exit, *}
import com.rockthejvm.utils.*
import scala.jdk.CollectionConverters.*

import java.nio.file.Paths

object Fibers extends ZIOAppDefault:
  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")

  // Fiber = lightweight thread
  def createFiber: Fiber[Throwable, String] = ??? // impossible to create manually

  // Sequential execution
  val sameThreadIO = for {
    mol <- meaningOfLife.debugThread
    lang <- favLang.debugThread
  } yield (mol, lang)

  // Parallel execution
  val differentThreadIO = for {
    _ <- meaningOfLife.debugThread.fork
    _ <- favLang.debugThread.fork
  } yield ()

  val meaningOfLifeFiber: ZIO[Any, Nothing, Fiber[Throwable, Int]] = meaningOfLife.fork

  // join a fiber
  def runOnAnotherThread[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    result <- fib.join
  } yield result

  // awaiting a fiber
  def runOnAnotherThread_v2[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    result <- fib.await
  } yield result match {
    case Exit.Success(value) => s"succeeded with $value"
    case Exit.Failure(cause) => s"failed with $cause"
  }

  // poll - peek at the result of the fiber RIGHT NOW, without blocking
  val peekFiber = for {
    fib <- ZIO.attempt {
      Thread.sleep(1000)
      42
    }.fork

    result <- fib.poll
  } yield result

  // compose fibers
  // zip
  val zippedFibers = for {
    fib1 <- ZIO.succeed("Result from fiber 1").debugThread.fork
    fib2 <- ZIO.succeed("Result from fiber 2").debugThread.fork
    fiber = fib1.zip(fib2)
    tuple <- fiber.join
  } yield tuple

  // orElse
  val chainedFibers = for {
    fiber1 <- ZIO.fail("not good!").debugThread.fork
    fiber2 <- ZIO.succeed("Rock the JVM!").debugThread.fork
    fiber = fiber1.orElse(fiber2)
    message <- fiber.join
  } yield message

  /*
  Exercises
  * */

  // 1. zip two fibers without using zip combinator. Only using fork and join
  // hint: create a fiber that waits for both
  def zipFibers[E, A, B](fiber1: Fiber[E, A], fiber2: Fiber[E, B]): ZIO[Any, Nothing, Fiber[E, (A, B)]] = for {
    newTask <- ZIO.succeed({
      for {
        r1 <- fiber1.join
        r2 <- fiber2.join
      } yield (r1, r2)
    })
    newFiber: Fiber[E, (A, B)] <- newTask.fork
  } yield newFiber

  // 2. same thing with orElse
  def chainFibers[E, A](fiber1: Fiber[E, A], fiber2: Fiber[E, A]): ZIO[Any, Nothing, Fiber[E, A]] = for {
    newTask <- ZIO.succeed {
      fiber1.join match
        case Exit.Success(v) => ZIO.succeed(v)
        case Exit.Failure(_) => fiber2.join match
          case Exit.Success(v2) => ZIO.succeed(v2)
          case Exit.Failure(e2) => ZIO.fail(e2.failureOption.get)
    }
    newFiber <- newTask.fork
  } yield newFiber

  // 3. distributing tasks in between many fibers
  // spawn n fibers, count the n of words in each file.
  // then aggregate all the results together in one big number
  def generateRandomFile(path: String): Unit = {
    val random = scala.util.Random
    val chars = 'a' to 'z'
    val nWords = random.nextInt(2000)
    val content = (1 to nWords)
      .map(_ => (1 to random.nextInt(10)).map(_ => chars(random.nextInt(26))).mkString) // one word for every 1 to nWords
      .mkString(" ")

    import java.io.{File, FileWriter}
    val writer = new FileWriter(new File(path))
    writer.write(content)
    writer.flush()
    writer.close()
  }

  def generateRandomFiles = ZIO.succeed((1 to 10).foreach(i =>
    generateRandomFile(s"src/main/resources/testfile_$i.txt")
  ))

  def countWords(content: String): Int =
    val count = content.split(" ").length
    println(s"Count: $count")
    count

  def countWordsInFile(path: String): Task[Int] = for {
    content <- ZIO.attempt(java.nio.file.Files.readAllLines(Paths.get(path)).asScala.mkString("\n"))
  } yield countWords(content)

  def countWords: Task[Int] = for {
    l <- ZIO.succeed((1 to 10)
      .map(i => for {
        fib <- countWordsInFile(s"src/main/resources/testfile_$i.txt").fork.debugThread
      } yield fib
      ))
    sumZio = l
      .map(b => b.flatMap(r => r.join))
      .reduce((x, y) => x.flatMap(vx => y.map(vy => vx + vy)))
    sum <- sumZio
  } yield sum

  //  def run = sameThreadIO
  def run = for {
    _ <- generateRandomFiles
    wordsCount <- countWords.debugThread
    _ <- ZIO.succeed(println(wordsCount))
  } yield ()
