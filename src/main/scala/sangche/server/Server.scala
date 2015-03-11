package sangche.server

import java.util

import akka.actor._
import akka.cluster._
import akka.cluster.ClusterEvent.{ReachabilityEvent, MemberEvent, LeaderChanged, MemberUp}
import sangche.msgs.Command
import language.postfixOps
import scala.collection.immutable.HashMap
import scala.collection.mutable.Map
import com.typesafe.config.ConfigFactory

import sangche.msgs.Messages._

class Server(cn: Int) extends Actor with ConsensusHandler {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
    cluster.subscribe(self, classOf[LeaderChanged])
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[ReachabilityEvent])
  }

  var db = HashMap.empty[Key, Value]      // assume persistent storage
  var plog = HashMap.empty[Int, Command]  // assume persistent storage
 
  // cluster leader
  var leader: Option[Address] = Option(cluster.selfAddress)

  //  members in cluster
  var members = Set.empty[Option[Address]]

  def isLeader(l: Option[Address]): Boolean = if (leader == None) false else l == leader

  def isLeader: Boolean = isLeader(Option(cluster.selfAddress))

  def isMajority(m: Int): Boolean = m > cn / 2

  def isMajorityUp: Boolean = isMajority(members.filterNot(_ == None).size)

  def receive = if (isMajorityUp) inService else notInService
}

object ServerStart {

  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [replica_server]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props(new Server(config.getInt("akka.cluster.nodes"))), name = "server")

    //TODO: graceful system shutdown
  }
}