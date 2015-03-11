package sangche.server

import akka.actor.{Cancellable, ActorRef}
import sangche.msgs.Messages._
import sangche.msgs._

import scala.concurrent.duration._

abstract class Entry[T](s: Server, ar: ActorRef) {

  implicit val _ = s.context.system.dispatcher

  val m = collection.mutable.Map[T, Int]().withDefaultValue(0)
  val timer: Cancellable

  def schedule = s.context.system.scheduler

  def isQUORUM = s.isMajority(m.values.max) && timer.cancel() // short circuit

  def isDone = (m.values.sum >= s.members.size) && timer.cancel() // short circuit

  def inc(v: T) = m(v) += 1

  def getClient = ar
}

class QEntry(n: Int, s: Server, ar: ActorRef) extends Entry[Value](s, ar) {
  val timer: Cancellable = schedule.scheduleOnce(50 milliseconds, s.self, TimeOut(n, "query"))

  def getConsensus = m.find(_._2 == m.values.max).get
}

class CEntry(n: Int, s: Server, ar: ActorRef) extends Entry[PrepareOK](s, ar) {
  val timer: Cancellable = schedule.scheduleOnce(50 milliseconds, s.self, TimeOut(n, "prepare"))
  lazy val timer2: Cancellable = schedule.scheduleOnce(50 milliseconds, s.self, TimeOut(n, "commit"))
  val m2 = collection.mutable.Map[CommitOK, Int]().withDefaultValue(0)

  def isCommitQUORUM = s.isMajority(m2.values.max) && timer2.cancel() // short circuit
    
  def isDone2 = (m2.values.sum >= s.members.size) && timer2.cancel() // short circuit

  def inc2(v: CommitOK) = m2(v) += 1
}



