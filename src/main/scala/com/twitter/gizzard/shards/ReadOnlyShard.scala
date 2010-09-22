package com.twitter.gizzard.shards

import scala.collection.mutable


class ReadOnlyShardFactory[ConcreteShard <: Shard](readWriteShardAdapter: ReadWriteShard[ConcreteShard] => ConcreteShard) extends shards.ShardFactory[ConcreteShard] {
  def instantiate(shardInfo: shards.ShardInfo, weight: Int, children: Seq[ConcreteShard]) =
    readWriteShardAdapter(new ReadOnlyShard(shardInfo, weight, children))
  def materialize(shardInfo: shards.ShardInfo) = ()
  def purge(shardInfo: shards.ShardInfo) = ()
}

class ReadOnlyShard[ConcreteShard <: Shard]
  (val shardInfo: ShardInfo, val weight: Int, val children: Seq[ConcreteShard])
  extends ReadWriteShard[ConcreteShard] {

  val shard = children.first

  def readOperation[A](method: (ConcreteShard => A)) = method(shard)

  def writeOperation[A](method: (ConcreteShard => A)) =
    throw new ShardRejectedOperationException("shard is read-only")

  def rebuildableReadOperation[A](method: (ConcreteShard => Option[A]))(rebuild: (ConcreteShard, ConcreteShard) => Unit) =
    method(shard)
}
