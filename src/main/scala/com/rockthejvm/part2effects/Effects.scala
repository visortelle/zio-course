package com.rockthejvm.part2effects

object Effects:
  // functional programming
  // EXPRESSIONS
  def combine(a: Int, b: Int): Int = a + b

  // local reasoning = type signature describes the kind of computation that will be performed
  // referential transparency = ability to replace an expression with the value that it evaluates to
  val five = combine(2, 3)
  val five_v2 = 2 + 3
  val five_v3 = 5

  // not all expressions are RT
  // example 1
  val resultOfPrinting: Unit = println("Learning ZIO")
  val resultOfPrinting_v2: Unit = () // not the same

  // example 2: changing a variable
  var anInt = 0
  val changingInt: Unit = (anInt = 42) // side effect
  val changingInt_v2: Unit = () // not the same program

  // side effects are inevitable

  /*
  Effect desires
  - the type signature describes what kind of computation it will perform
  - the type signature describes the type of VALUE that it will produce
  - if side effects are required, construction must be separate from the EXECUTION
   */

  /*
  Example: Options = possibly absent values
  - type signature describes the kind of computation = a possibly absent value
  - type signature says that the computation returns an A, if the computation produce something
  - no side effects are needed

  => Option is an effect
  */
  val anOption: Option[Int] = Option(42)

  /*
  Example 2: Future
  - [x] describes asynchronous computation
  - [x] produces a value of type A, if it finishes and it's successful
  - [ ] side effects are required, construction is NOT SEPARATE FROM EXECUTION

  => Future is NOT an effect
  */

  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global

  val aFuture: Future[Int] = Future(42)

  /*
  Example 3: MyIO
  - [x] describes any kind of computation, including those performing side effects
  - [x] produces values of type A if the computation is successful
  - [x] side effects are required, construction IS SEPARATE from execution

  => MY IO IS AN EFFECT!
  * */
  case class MyIO[A](unsafeRun: () => A) {
    def map[B](f: A => B): MyIO[B] =
      MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun()).unsafeRun())
  }

  val anIOWithSideEffects = MyIO(() => {
    println("producing effect")
    42
  })

  /*
  Exercises - create some IO which
  - 1. measure the current time of the system
  - 2. measure the duration of a computation
    - use exercise 1
    - use map/flatMap combination of MyIO
  - 3. read something from the console
  - 4. print something to the console (e.g. "what's your name?"), then read, then print a welcome message
  */

  def measureTime: MyIO[Long] = MyIO(() => java.time.Instant.now.toEpochMilli)

  // 2
  def measureComputationDuration[A](computation: MyIO[A]): MyIO[(Long, A)] = measureTime.flatMap(
    st => computation.flatMap(
      c => measureTime.map(
        et => (et - st, c)
      )))

  def readFromConsole: MyIO[String] = MyIO(() => scala.io.StdIn.readLine)

  def printToConsole(string: String): MyIO[Unit] = MyIO(() => println(string))

  def task4: MyIO[Unit] = printToConsole("Welcome! Please type your name.").flatMap(
    _ => readFromConsole.flatMap(name =>
      printToConsole(s"Your name is $name")
    )
  )

  def main(args: Array[String]): Unit =
    anIOWithSideEffects.unsafeRun()
    val time = measureTime.unsafeRun()
    println(s"Current time: $time")

    val duration = measureComputationDuration(measureTime).unsafeRun()
    println(s"Duration: $duration")

    task4.unsafeRun()

