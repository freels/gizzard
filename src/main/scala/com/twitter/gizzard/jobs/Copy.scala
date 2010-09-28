package com.twitter.gizzard.jobs

import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger
import com.twitter.ostrich.Stats

import scheduler.JobScheduler
import nameserver._
import shards._


object Copy {
  val MIN_COPY = 500
}

trait CopyFactory[S <: shards.Shard] extends ((ShardId, ShardId) => Copy[S])

trait CopyParser[S <: shards.Shard] extends jobs.UnboundJobParser[(NameServer[S], JobScheduler)] {
  def apply(attributes: Map[String, Any]): Copy[S]
}

abstract case class Copy[S <: shards.Shard](sourceId: ShardId, destinationId: ShardId, var count: Int) extends UnboundJob[(NameServer[S], JobScheduler)] {
  private val log = Logger.get(getClass.getName)

  def toMap = {
    Map("source_shard_hostname" -> sourceId.hostname,
        "source_shard_table_prefix" -> sourceId.tablePrefix,
        "destination_shard_hostname" -> destinationId.hostname,
        "destination_shard_table_prefix" -> destinationId.tablePrefix,
        "count" -> count
    ) ++ serialize
  }

  def finish(nameServer: NameServer[S], scheduler: JobScheduler) {
    nameServer.markShardBusy(destinationId, Busy.Normal)
    log.info("Copying finished for (type %s) from %s to %s",
             getClass.getName.split("\\.").last, sourceId, destinationId)
    Stats.clearGauge(gaugeName)
  }

  def apply(environment: (NameServer[S], JobScheduler)) {
    val (nameServer, scheduler) = environment
    try {
      log.info("Copying shard block (type %s) from %s to %s: state=%s",
               getClass.getName.split("\\.").last, sourceId, destinationId, toMap)
      val sourceShard = nameServer.findShardById(sourceId)
      val destinationShard = nameServer.findShardById(destinationId)
      // do this on each iteration, so it happens in the queue and can be retried if the db is busy:
      nameServer.markShardBusy(destinationId, Busy.Busy)

      val nextJob = copyPage(sourceShard, destinationShard, count)
      nextJob match {
        case Some(job) => {
          incrGauge
          scheduler(job)
        }
        case None => finish(nameServer, scheduler)
      }
    } catch {
      case e: NonExistentShard =>
        log.error("Shard block copy failed because one of the shards doesn't exist. Terminating the copy.")
      case e: ShardTimeoutException if (count > Copy.MIN_COPY) =>
        log.warning("Shard block copy timed out; trying a smaller block size.")
        count = (count * 0.9).toInt
        scheduler(this)
      case e: ShardDatabaseTimeoutException =>
        log.warning("Shard block copy failed to get a database connection; retrying.")
        scheduler(this)
      case e: Throwable =>
        log.warning("Shard block copy stopped due to exception: %s", e)
        throw e
    }
  }

  def copyPage(sourceShard: S, destinationShard: S, count: Int): Option[Copy[S]]
  def serialize: Map[String, Any]

  private def incrGauge = {
    Stats.setGauge(gaugeName, Stats.getGauge(gaugeName).getOrElse(0.0) + count)
  }

  private def gaugeName = {
    "x-copying-" + sourceId + "-" + destinationId
  }
}
