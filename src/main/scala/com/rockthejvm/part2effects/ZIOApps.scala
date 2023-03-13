package com.rockthejvm.part2effects

import zio.*

object ZIOApps extends ZIOAppDefault {
  val meaningOfLife: UIO[Int] = ZIO.succeed(42)

  override def run: ZIO[Any, Any, Any] = meaningOfLife.debug

}
