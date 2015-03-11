package sangche.msgs

import akka.actor.Address

object Messages {
  type Message = String
  type Key = String
  type Value = Any
}

import Messages._

case class TimeOut(n: Int, msg: Message)

//case class TakeOver(seq: Int)

case class Command(k: Key, v: Value)

case class CommandOK(msg: Message)

case class CommandFail(sn: Int, msg: Message)

case class Prepare(sn: Int, data: Command)

case class PrepareOK(sn: Int)

case class Commit(sn: Int)

case class CommitOK(sn: Int)

case class Query(k: Key)

case class QueryOK(msg: Value)

case class QueryFail(sn: Int, msg: Message)

case class PeerQuery(s: Int, k: Key)

case class PeerQueryRes(s: Int, k: Key, v: Value)

case class PING(n: Int)

case class PONG(_seq: Int, _members: Set[Option[Address]])

