package com.rockthejvm.part1recap

import java.util.concurrent.Executors
import scala.util.{Success, Failure}
import scala.concurrent.{ExecutionContext, Future}

class Essentials {
  val aBoolean: Boolean = false

  val anIfExpressions = if (2 > 3) "bigger" else "smaller"

  val theUnit = println("hello, Scala")

  class Animal
  class Cat extends Animal

  trait Carnivore {
    def eat(animal: Animal): Unit
  }

  // Inheritance model:
  class Crocodile extends Animal with Carnivore {
    override def eat(animal: Animal): Unit = println("Crunch!")
  }

  // singleton
  object MySingleton

  // companions
  object Carnivore

  // generics
  class MyList[A]

  // method notation
  val three = 1 + 2
  val anotherThree = 1.+(2)

  // functional programming
  val incrementor: Int => Int = x => x + 1
  val incremented = incrementor(45) // 46

  val processedList = List(1, 2, 3).map(incrementor) // List(2, 3, 4)

  val aLongerList = List(1, 2, 3).flatMap(x => List(x, x + 1)) // List(1,2, 2,3, 3,4)

  // For comprehension
  val checkerboard = List(1, 2, 3).flatMap(n => List('a', 'b', 'c').map(c =>  (n, c)))
  val anotherCheckerboard = for {
    n <- List(1, 2, 3)
    c <- List('a', 'b', 'c')
  } yield (n, c)

  // options and try
  val anOption = Option(3)

  // pattern matching
  val anUnknown: Any = 45

  val ordinal = anUnknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  // Futures
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
  val aFuture = Future(42)

  // wait for completion
  aFuture.onComplete {
    case Success(value) => println(s"The async meaning of life is $value")
    case Failure(exception) => println(s"Meaning of failure $exception")
  }

  val anotherFuture = aFuture.map(_ + 1) // Future(43) when it completes

  // partial functions
  val aPartialFunction: PartialFunction[Int, Int] = {
    case 1 => 43
    case 8 => 56
    case 342 => 423423
  }

  // some more advanced stuff
  trait HigherKindedType[F[_]]
  trait SequenceChecker[F[_]] {
    def isSequential: Boolean
  }

  val listChecker = new SequenceChecker[List] {
    override def isSequential: Boolean = true
  }

  def main(args: Array[String]): Unit = {

  }
}
