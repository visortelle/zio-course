package com.rockthejvm.part1recap

import java.util

class Variance {
  // OOP - substitution
  class Animal

  class Dog(name: String) extends Animal

  // Variance question for List: if Dog <: Animal, then should List[Dog] <: List[Animal]?

  // YES - COVARIANT
  val lessie = new Dog("Lessie")
  val hachi = new Dog("Hachi")
  val laika = new Dog("Laika")

  val anAnimal: Animal = lessie
  val someAnimal: List[Animal] = List(lessie, hachi, laika)

  class MyList[+A] // MyList is COVARIANT in A

  val myAnimalList: MyList[Animal] = new MyList[Dog]

  // NO - then the type is INVARIANT
  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  // all generics in Java are INVARIANT
  // val aJavaList: java.util.ArrayList[Animal] = new util.ArrayList[Dog]() // Error - type mistmatch


  // HELL NO - CONTRAVARIANCE
  trait Vet[-A] {
    def heal(animal: A): Boolean
  }

  // Vet[Animal] is "better" than a Vet[Dog]: she/he can treat ANY animal, therefore my dog a well
  // Dog <: Animal, then Vet[Dog] >: Vet[Animal]
  val myVet: Vet[Dog] = new Vet[Animal] {
    override def heal(animal: Animal): Boolean = {
      println("Here you go")
      true
    }
  }

  val healingLessie = myVet.heal(lessie)

  /*
    Rule of thumb:
    - if a type PRODUCES or RETRIEVES values of type A (e.g. lists), then the type should be COVARIANT
    - if the type CONSUMES or ACTS ON values of type A (e.g. a vet), then the type should be CONTRAVARIANT
    - otherwise, INVARIANT
  * */

  /**
   * Variance positions
   */
  class Cat extends Animal

  val Garfield = new Cat

  /**
   * class Vet2[-A](val favoriteAnimal: A) // <-- the types of val fields are in COVARIANT position
   * val theVet: Vet2[Animal] = new Vet2[Animal](garfield)
   * val dogVet: Vet2[Dog] = theVet
   * val favAnimal: Dog = dogVet.favoriteAnimal // must be a Dog - type conflict
   */

  // var fields are also in COVARIANT position (same)

  /**
   * class MutableContainer[+A](var contents: A)
   * val containerAnimal: MutableContainer
   */

  /** Error: Covariant type A occurs in contravariant position in type A of value el
  class MyList2[+A]:
    def hello(el: A) = ???
  */

  /** solution: WIDEN the type argument
   * class MyList2[+A]:
   *   def hello[B >: A](el: B): MyList2[B] = ???
   */

  /**
   * Method return types
   * abstract class Vet2[-A]:
   *   def rescueAnimal(): A
   *
   * val vet: Vet2[Animal]
   */

  def main(args: Array[String]): Unit = {

  }
}