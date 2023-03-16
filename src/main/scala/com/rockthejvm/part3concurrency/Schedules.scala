package com.rockthejvm.part3concurrency

import zio.*
import com.rockthejvm.utils.debugThread

object Schedules extends ZIOAppDefault:
  val aZIO = Random.nextBoolean.flatMap { flag =>
    if flag
    then ZIO.succeed("fetched value!").debugThread
    else ZIO.succeed("failure...").debugThread *> ZIO.fail("error")
  }

  val aRetriedZIO = aZIO.retry(Schedule.recurs(10)) // tries 10 times, returns the first success, last failure

  // schedules are data structures that describe HOW effects should be timed
  val oneTimeSchedule = Schedule.once
  val recurrentSchedule = Schedule.recurs(10)
  val fixedInternalSchedule = Schedule.spaced(1.second) // repeats every second until a success is returned
  // exponential backoff
  val exBackoffSchedule = Schedule.exponential(1.second, 2.0)
  val fiboSchedule = Schedule.fibonacci(1.second) // 1s, 1s, 2s, 3s, 5s, ...

  // combinators
  var recurrentAndSpaced = Schedule.recurs(3) && Schedule.spaced(1.second) // every attempt is 1s apart, 3 attempts total
  // sequencing
  var recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second)

  // Schedules have
  // R = environment
  // I = input (errors in the case or .retry, values in the case of .repeat)
  // O = output (values for the next schedule so that you can do something with them)
  val totalElapsed = Schedule.spaced(1.second) >>> Schedule.elapsed.map(time => println(s"total time elapsed $time"))

  def run = aZIO.retry(totalElapsed)
