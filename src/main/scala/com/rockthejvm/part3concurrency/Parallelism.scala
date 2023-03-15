package com.rockthejvm.part3concurrency

import com.rockthejvm.part3concurrency.Fibers.generateRandomFile
import zio.*
import scala.jdk.CollectionConverters.*
import com.rockthejvm.utils.debugThread

import java.nio.file.Paths

object Parallelism extends ZIOAppDefault {
  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")
  val combined = meaningOfLife.zip(favLang) // combines/zips in a sequential way

  // combine in parallel
  val combinedPar = meaningOfLife.zipPar(favLang) // combination is parallel

  /*
   - start each effect on fiber
   - what if one fails? the other one should be interrupted
   - what if one is interrupted? the entire thing should be interrupted
   - what if the whole thing is interrupted? need to interrupt both effects
   * */

  /*
  Exercise:
  try zipPar combinator
  hint: fork/join/await, interrupt
  * */
  def myZipPar[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, (A, B)] =
    val fiba = zioa.fork
    val fibb = ziob.fork

    val zioab = for {
      fibar <- fiba
      fibbr <- fibb

      _ <- fiba.onInterrupt(fibbr.interrupt)
      _ <- fibb.onInterrupt(fibar.interrupt)

      _ <- fiba.onError(_ => fibbr.interrupt)
      _ <- fibb.onError(_ => fibar.interrupt)

      ra <- fibar.join
      rb <- fibbr.join
    } yield (ra, rb)

    for {
      zioabr <- zioab.awaitAllChildren
    } yield zioabr

  // parallel combinators
  // zipPar, zipWithPar

  // collectAllPar
  val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // "traverse"

  // foreachPar
  val printlnParallel = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAllPar
  val sumPar = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  val sumPar_v2 = ZIO.mergeAllPar(effects)(0)(_ + _)

  /*
  - if all effects succeed, all good
  - one effect fails => everyone else is interrupted, error is surfaced
  - one effect is interrupted => everyone else is interrupted, error = interruption (for the big expression)
  - if the entire thing is interrupted => all effects are interrupted
  * */

  /*
  Exercise: do the word counting exercise, using parallel combinators
  * */
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
    l <- ZIO.succeed((1 to 10).map(i => countWordsInFile(s"src/main/resources/testfile_$i.txt")))
    sumEffect = ZIO.reduceAllPar(ZIO.succeed(0), l)((a, b) => a + b)
    sum <- sumEffect
  } yield sum

  def run = countWords.debugThread
}
