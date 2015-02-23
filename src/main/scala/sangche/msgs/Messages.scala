package sangche.msgs

import akka.actor.Address

object Messages {
  type Message = String
  type Key = Any
  type Value = Any
}

import Messages._

case class TimeOut(n: Int, msg: Message)

//case class TakeOver(seq: Int)

case class Command(k: Key, v: Value)

case class CommandOK(msg: Message)

case class CommandFail(msg: Message)

case class Prepare(sn: Int)

case class PrepareOK(sn: Int)

case class Commit(sn: Int, msg: Command)

case class CommitOK(sn: Int)

case class Query(msg: Message)

case class QueryOK(msg: Value)

case class QueryFail(msg: Message)

case class PeerQuery(s: Int, msg: Message)

case class PeerQueryRes(s: Int, k: Key, v: Value)

case class PING(n: Int)

case class PONG(_seq: Int, _members: Set[Option[Address]])

