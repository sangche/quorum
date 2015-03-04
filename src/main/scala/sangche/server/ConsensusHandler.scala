package sangche.server

import akka.cluster.ClusterEvent._
import akka.util.Timeout
import language.postfixOps
import akka.actor._

import sangche.msgs._

trait ConsensusHandler extends ActorLogging with Stash {
  this: Server =>

  var seqno = 0
  // Command seq no.
  var qseqno = 0
  // Query seq no.
  var tmp = 0 // in case messages loss

  implicit val _ = context.system.dispatcher

  private def select(m: Address, s: String) = context.actorSelection(RootActorPath(m) / "user" / s)

  private def xs = members.filterNot(_ == None).map {
    m => select(m.get, "server")
  }

  def inService: Receive = if (isLeader) {
    log.info(s"\n==> server state become leading")
    leading
  } else {
    log.info(s"\n==> server state become following")
    following
  }

  def commandProcessing: Receive = {

    case Prepare(n) if n == seqno =>
      log.info(s"\n==> n = $n, tmp = $tmp")
      if (tmp == 0) {
        tmp = n
        sender ! PrepareOK(n)
      } else {
        log.info(s"\n==> Unexpected: This should not happen.")
      }

    case Prepare(n) =>
      log.info(s"\n==> Unexpected: got Prepare($n). sender ${sender}, self $self")

    case Commit(n, c) if n == seqno =>
      log.info(s"\n==> n = $n, tmp = $tmp")
      if (n == tmp) {
        tmp = 0
        sender ! CommitOK(n)
      } else {
        log.info(s"\n==> Unexpected: This should not happen")
      }

    case Commit(n, c) =>
      log.info(s"\n==> Unexpected: got Commit($n, $c). sender $sender, self $self, seqno $seqno")

    case po@PrepareOK(n) if n == seqno =>
      val e = ctabs(seqno)
      e.inc(po)
      if (e.isQUORUM) {
        xs foreach {
          _ ! Commit(seqno, e.c)
        }
        e.timer2
      }
      else if (e.isDone) {
        e.getClient ! CommandFail("PrepareOK quorum failed")
        ctabs(seqno) = null
        context.unbecome
        unstashAll()
      }

    case po@PrepareOK(n) if n < seqno =>
      log.info(s"\n==> $po, seqno = $seqno. Not implemented yet")

    //even with timeout during waiting for CommitOK, followers might already commit. 
    //so Query quorum for this Command might already be established! (ajust timeout)
    case c@CommitOK(n) if n == seqno =>
      val e = ctabs(seqno)
      //if (e != null) {  //no need worry
      e.inc2(c)
      if (e.isCommitQUORUM) {
        db += e.c.k -> e.c.v
        e.getClient ! CommandOK(s"$n - Success")
        ctabs(seqno) = null
        //more CommitOK(n) may come, which will cause null exception?
        //-> No, it comes after state transition to leading.
        context.unbecome
        unstashAll()
      } else if (e.isDone2) {
        e.getClient ! CommandFail("CommitOK quorum failed")
        ctabs(seqno) = null
        context.unbecome
        unstashAll()
      }
    //}

    case CommitOK(n) => //same reason
      log.info(s"\n==> n = $n, seqno = $seqno. this commitok is not implemented yet")

    case TimeOut(n, msg) if n == seqno =>
      if (msg == "query") {
        val e = qtabs(n)
        e.getClient ! QueryFail("query response timeout*")
      } else {
        val e = ctabs(n)
        e.getClient ! CommandFail(msg + " response timeout*")
      }
      context.unbecome
      unstashAll()

    case other => //including next Command(_,_)
      stash()
  }

  def leading: Receive = {
    case LeaderChanged(newLeader) =>
      leader = newLeader
      if (isLeader)
        log.info(s"\n==> state stays leading")
      else {
        log.info(s"\n==> state from leading to following")
        context.become(following, true)
      }

    case c@Command(_, _) =>
      seqno += 1
      ctabs += seqno -> new CEntry(seqno, this, c, sender)
      xs foreach { x =>
        x ! Prepare(seqno)
      }
      context.become(commandProcessing, discardOld = false)

    case other => common(other, true)
  }

  def following: Receive = {

    case LeaderChanged(newLeader) =>
      leader = newLeader
      log.info(s"\n==> leader changed to: $newLeader")
      if (isLeader) {
        log.info(s"\n==> state from following to leading")
        context.become(leading, true)
      }

    case c@Command(_, _) =>
      leader match {
        case None => sender ! CommandFail("Leader is None")
        case Some(l) =>
          import akka.pattern.ask
          import scala.concurrent.duration._
          import scala.util.{Try, Success, Failure}

          val client = sender
          val ldr = select(l, "server")

          (ldr ? PING(0))(Timeout(25 milliseconds)).mapTo[PONG] onComplete {
            case Success(PONG(0, _)) =>
              log.info(s"\n==> Fowarding $c to leader $leader, $sender")
              ldr.tell(c, client)
            case fail =>
              log.info(s"\n==> no PONG. leader not ready? -- $fail")
              client ! CommandFail("leader not ready yet")
          }
      }

    case Prepare(n) =>
      log.info(s"\n==> n = $n, tmp = $tmp")
      if (tmp == 0) {
        tmp = n
        sender ! PrepareOK(n)
      } else {
        //unimplemented yet
        sender ! PrepareOK(tmp)
      }

    case Commit(n, msg) =>
      log.info(s"\n==> n = $n, tmp = $tmp")
      if (n == tmp) {
        seqno = tmp
        tmp = 0
        db += msg.k -> msg.v
        sender ! CommitOK(n)
      } else {
        log.info(s"\n==> seqno = $seqno, Commit($n), tmp = $tmp not implemented yet")
      }

    case other => common(other, false)
  }

  def common(o: Any, s: Boolean) = o match {

    case TimeOut(n, msg) =>
      if (msg == "query") {
        val e = qtabs(n)
        e.getClient ! QueryFail("query response timeout")
      } else {
        val e = ctabs(n)
        e.getClient ! CommandFail(msg + " response timeout. unexpected case!")
      }

    case Query(key) =>
      qseqno += 1
      qtabs += qseqno -> new QEntry(qseqno, this, sender)
      xs foreach { x =>
        x ! PeerQuery(qseqno, key)
      }

    case PeerQuery(_seq, key) =>
      sender ! PeerQueryRes(_seq, key, db.get(key))

    case PeerQueryRes(_seq, key, value) if _seq == qseqno =>
      val e: QEntry = qtabs(_seq)
      if (e != null) {
        e.inc(value)
        if (e.isQUORUM) {
          //cancel() on cancelled return false. next PeerQueryRes can't pass if condition.
          val from = if(s) "From leader" else "From follower"
          e.get._1 match {
            case None => e.getClient ! QueryFail(from + s": no such data for $key")
            case Some(v) => e.getClient ! QueryOK(from + ": " + v)
          }
          qtabs(_seq) = null
        }
        else if (e.isDone) {
          e.getClient ! QueryFail("consensus failed")
          qtabs(_seq) = null
        }
      }

    case MemberRemoved(member, oldStatus) => /*
      members -= Option(member.address)
      if (!isMajorityUp) {
        println("goto notInService State...")
        context.become(notInService, true)
      }*/

    case UnreachableMember(member) =>
      log.info(s"\n==> Member unreachable: $member")
      if (member == leader) leader = None
      members -= Option(member.address)
      if (!isMajorityUp) {
        context.become(notInService, true)
      }

    case MemberUp(member) =>
      val lorf = if (s) "[leading state] " else "[following state] "
      log.info(s"\n==> $lorf member up: $member")
      members += Option(member.address)

    case PING(n) =>
      sender ! PONG(n, members)

    case other => log.info(s"\n==> $s state. extra CommitOK expected. others unexpected: $other")
  }

  def notInService: Receive = {

    case LeaderChanged(newLeader) =>
      leader = newLeader
      if (isLeader)
        log.info(s"\n==> leader in NIS")
      else
        log.info(s"\n==> follower in NIS")
      if (isMajorityUp) context.become(inService, true)

    case MemberRemoved(member, oldStatus) =>
      log.info(s"\n==> Member removed: $member")
    //members -= Option(member.address)

    case UnreachableMember(member) =>
      log.info(s"\n==> Member unreachable: $member")
      members -= Option(member.address)

    case MemberUp(member) =>
      log.info(s"\n==> member up: $member")
      members += Option(member.address)
      if (isMajorityUp) {
        log.info(s"\n==> moving to inService state...")
        context.become(inService, true)
      }

    case Command(k, v) => sender ! CommandFail(s"$k - NIS. Try again later")

    case Query(k) => sender ! QueryFail(s"$k - NIS. Try again later")

    case PING(n) =>
      sender ! PONG(n, members)

    case other => log.info(s"\n==> NIS. message: $other")
  }
}
