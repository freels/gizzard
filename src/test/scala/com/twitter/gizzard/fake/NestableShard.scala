package com.twitter.gizzard.fake

import shards.ShardException
import org.specs.mock.{ClassMocker, JMocker}
import scala.collection.mutable
import shards.ShardInfo

class NestableShardFactory extends shards.ShardFactory[Shard] {
  def instantiate(shardInfo: ShardInfo, weight: Int, children: Seq[Shard]) = new NestableShard(shardInfo, weight, children)
  def materialize(shardInfo: ShardInfo) = ()
  def purge(shardInfo: ShardInfo) = ()
}

class NestableShard(val shardInfo: shards.ShardInfo, val weight:Int, val children: Seq[fake.Shard]) extends Shard {
  val map = new mutable.HashMap[String, String]

  def get(key: String) = {
    map.get(key)
  }

  def put(key: String, value: String) = {
    map.put(key, value)
    value
  }

}
