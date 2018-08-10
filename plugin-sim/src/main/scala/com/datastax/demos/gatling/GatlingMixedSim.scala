package com.datastax.demos.gatling

import java.util.UUID
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import com.datastax.driver.core.ConsistencyLevel._
import com.datastax.driver.core.{Session => _, _}
import com.datastax.driver.dse.{DseCluster, DseSession}
import com.datastax.gatling.plugin.DsePredef._
import com.datastax.gatling.plugin.DseProtocolBuilder
import com.github.javafaker.Faker
import io.gatling.core.Predef.{exec, _}
import io.gatling.core.scenario.Simulation
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration.FiniteDuration

class GatlingMixedSim extends Simulation {
  // setting the cluster & open connection
  val clusterBuilder = new DseCluster.Builder

  clusterBuilder.
    withSocketOptions(new SocketOptions().
      setKeepAlive(true).
      setTcpNoDelay(true)).
    withQueryOptions(new QueryOptions().
      setDefaultIdempotence(true).
      setPrepareOnAllHosts(true).
      setReprepareOnUp(true).
      setConsistencyLevel(LOCAL_ONE)).
    withPoolingOptions(new PoolingOptions().
      setCoreConnectionsPerHost(HostDistance.LOCAL, 1).
      setMaxConnectionsPerHost(HostDistance.LOCAL, 2).
      setNewConnectionThreshold(HostDistance.LOCAL, 30000).
      setMaxRequestsPerConnection(HostDistance.LOCAL, 30000))

  val srvList = System.getProperty("contactPoints", "127.0.0.1").split(",").toList
  srvList.foreach(clusterBuilder.addContactPoint)

  val cluster: DseCluster = clusterBuilder.build()
  val dseSession: DseSession = cluster.connect()

  val cqlConfig: DseProtocolBuilder = cql.session(dseSession)

  // prepare queries (schema should already exists in database - execute 'cql/create-schema.cql' script before)
  val insertPrepared: PreparedStatement = dseSession.prepare(
    "insert into gatling.oauth_tokens(id, nonce, user, created, expires, attributes) values(?,?,?,?,?,?)");
  val updatePrepared: PreparedStatement = dseSession.prepare(
    "update gatling.oauth_tokens set nonce = ? where id = ?")
  val selectPrepared = dseSession.prepare("select user, nonce from gatling.oauth_tokens where id = ?")

  // Random generator for our feeder
  def random: ThreadLocalRandom = {
    ThreadLocalRandom.current()
  }

  // this feader will "feed" random data into our Gatling Sessions
  val defaultTTL = 30 * 60 // 30 minutes
  val faker = new Faker()
  val feeder = Iterator.continually({
    val start = System.nanoTime()
    val timestamp = java.time.Instant.now()
    val m = Map(
      "token_id" -> UUID.randomUUID(),
      "user" -> faker.name().username(),
      "nonce1" -> random.nextInt(),
      "nonce2" -> random.nextInt(),
      "created" -> timestamp.toEpochMilli,
      "expires" -> timestamp.plusSeconds(defaultTTL).toEpochMilli,
      "attrs" -> Map(
        "code" -> faker.code.isbn13(),
        "address" -> faker.address().fullAddress()
      ),
      "ttl" -> defaultTTL
    )
    val end = System.nanoTime()
//    println("Generating next value took " + TimeUnit.NANOSECONDS.toMicros(end - start) + "microseconds")
    m
  }
  )

  // define scenario
  val loadData: ScenarioBuilder = scenario("loadData")
    .feed(feeder)
    .group("Insert")(
      exec(cql("InsertOAuth")
        .executePrepared(insertPrepared)
        .withParams(List("token_id", "nonce1", "user", "created", "expires", "attrs")))
    )
    .pause(1) // wait 1 second before selecting token...
    .group("Select")(
      exec(cql("SelectOAuth")
        .executePrepared(selectPrepared)
        .withParams(List("token_id"))
        // check that we received data from DSE
        .check(rowCount.greaterThan(0))
        .check(resultSet.saveAs("fetchedData"))
        .check(columnValue("user").is(session => session("user").as[String]))
        .check(columnValue("nonce").is(session => session("nonce1").as[Int]))
      )
    )
    .group("Update")(
      exec(cql("updateOAuth")
        .executePrepared(updatePrepared)
        .withParams(List("nonce2", "token_id")))
    )

  // setup & execute simulation...
  val testDuration = FiniteDuration(java.lang.Long.getLong("testDuration", 5), TimeUnit.MINUTES)
  val concurrentSessionCount: Int = Integer.getInteger("concurrentSessionCount", 100)

  setUp(
    loadData.inject(
      constantUsersPerSec(concurrentSessionCount) during testDuration
    )
  ).assertions(
    global.failedRequests.count.is(0)
  ).protocols(cqlConfig)

  after(cluster.close())
}
