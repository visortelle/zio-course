package com.rockthejvm.part2effects

import zio.*

import java.util.concurrent.TimeUnit

object ZIODependencies extends ZIOAppDefault:
  // app to subscribe users to newsletter
  case class User(name: String, email: String)

  class UserSubscription(emailService: EmailService, userDatabase: UserDatabase):
    def subscribeUser(user: User): Task[Unit] = for {
      _ <- emailService.email(user)
      _ <- userDatabase.insert(user)
    } yield ()

  object UserSubscription:
    def create(emailService: EmailService, userDatabase: UserDatabase) =
      new UserSubscription(emailService, userDatabase)

    def live: ZLayer[EmailService & UserDatabase, Nothing, UserSubscription] =
      ZLayer.fromFunction(create)

  class EmailService:
    def email(user: User): Task[Unit] =
      ZIO.succeed(println(s"You're just been subscribe to Rock the JVM. Welcome, ${user}."))

  object EmailService:
    def create = new EmailService
    def live: ZLayer[Any, Nothing, EmailService] = ZLayer.succeed(create)

  class UserDatabase(connectionPool: ConnectionPool):
    def insert(user: User): Task[Unit] = for {
      conn <- connectionPool.get
      _ <- conn.runQuery(s"INSERT INTO subscribers(name, email) values (${user.name}, ${user.email})")
    } yield ()

  object UserDatabase:
    def create(connectionPool: ConnectionPool) = new UserDatabase(connectionPool)
    def live: ZLayer[ConnectionPool, Nothing, UserDatabase] = ZLayer.fromFunction(create)

  class ConnectionPool(nConnections: Int):
    def get: Task[Connection] =
      ZIO.succeed(println("Acquired connection")) *> ZIO.succeed(Connection())

  object ConnectionPool:
    def create(nConnections: Int) = new ConnectionPool(nConnections)
    def live(nConnections: Int) = ZLayer.succeed(create(nConnections))

  class Connection():
    def runQuery(query: String): Task[Unit] =
      ZIO.succeed(println(s"Executing query: $query"))

  val subscriptionService = ZIO.succeed( // Dependency injection
    UserSubscription.create(
      EmailService.create,
      UserDatabase.create(
        ConnectionPool.create(10)
      )
    )
  )

  /*
  "clean DI" has drawbacks
  - does not scale for many services
  - DI can be 100x worse
    - pass dependencies partially
    - not having all dependencies in the same place
    - passing dependencies multiple times
  */

  def subscribe(user: User): ZIO[Any, Throwable, Unit] = for {
    sub <- subscriptionService // service is instantiated at the point of call
    _ <- sub.subscribeUser(user)
  } yield ()

  // risk leaking resources if you subscribe multiple users in the same program

  val program = for {
    _ <- subscribe(User("Kiryl", "kiryl@gmail.com"))
    _ <- subscribe(User("Karyna", "karyna@gmail.com"))
  } yield ()

  // alternative
  def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] = for {
    sub <- ZIO.service[UserSubscription] // ZIO[UserSubscription, Nothing, UserSubscription]
    _ <- sub.subscribeUser(User("Kiryl", "kiryl@gmail.com"))
    _ <- sub.subscribeUser(User("Karyna", "karyna@gmail.com"))
  } yield ()

  def program_v2 = for {
    _ <- subscribe_v2(User("Kiryl", "kiryl@gmail.com"))
    _ <- subscribe_v2(User("Karyna", "karyna@gmail.com"))
  } yield ()

  /*
  - we don't need to care about dependencies until the end of the world
  - all ZIOs requiring this dependency will use the same instance
  - can use different instances of the same type for different needs (e.g. for testing)
  - layers can be created and composed much like regular ZIOs + rich API
  */


  /**
   * ZLayers
   */
  val connectionPoolLayer: ZLayer[Any, Nothing, ConnectionPool] = ZLayer.succeed(ConnectionPool.create(10))
  //
  val databaseLayer = ZLayer.fromFunction(UserDatabase.create)
  val emailServiceLayer = ZLayer.succeed(EmailService.create)
  val userSubscriptionServiceLayer: ZLayer[UserDatabase & EmailService, Nothing, UserSubscription] =
    ZLayer.fromFunction(UserSubscription.create)

  // compose layers
  // vertical composition >>>
  val databaseLayerFull: ZLayer[Any, Nothing, UserDatabase] = connectionPoolLayer >>> databaseLayer
  // horizontal composition: combines the dependencies of both layers AND the values of both layers
  val subscriptionRequirementsLayer: ZLayer[Any, Nothing, UserDatabase & EmailService] =
    databaseLayerFull ++ emailServiceLayer
  // mix & match
  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription] =
    subscriptionRequirementsLayer >>> userSubscriptionServiceLayer

  // best practice: write "factory" methods exposing layers in the companion objects of the services

  def runnableProgram = program_v2.provide(userSubscriptionLayer)

  // magic
  val runnableProgram_v2 = program_v2.provide(
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
    // ZIO will tell you if you're missing a layer
    // and if you have multiple layers of the same type
    ZLayer.Debug.tree
  )

  // magic v2
  val userSubscriptionLayer_v2: ZLayer[Any, Nothing, UserSubscription] = ZLayer.make[UserSubscription](
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
  )

  // passthrough
  val dbWithPoolLayer: ZLayer[ConnectionPool, Nothing, ConnectionPool & UserDatabase] = UserDatabase.live.passthrough
  // service = take a dependency and expose it as a value to further layers
  val dbService = ZLayer.service[UserDatabase]
  // launch
  val subscriptionLaunch: ZIO[EmailService & UserDatabase, Nothing, Nothing] = UserSubscription.live.launch
  // memoization <ZLayer>.fresh instantiates a new layer

  /*
  Already provided services: Clock, Random, System, Console
  * */
  val getTime = Clock.currentTime(TimeUnit.SECONDS)
  val randomValue = Random.nextInt
  val sysVariable = System.env("ABC")
  val printlnEffect = Console.printLine("This is ZIO")

  def run = runnableProgram_v2
