package sangche.client

import akka.actor.ActorDSL._
import akka.actor._
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import scala.language.postfixOps
import scala.util.{Success, Failure, Random}
import scala.collection.immutable.HashMap

import sangche.msgs.Messages._
import sangche.msgs._


object Agent extends App {
  val cfg =
    """
akka {
      |  loglevel = "INFO"
      |  actor {
      |     provider = "akka.remote.RemoteActorRefProvider"
      |  }
      |  remote {
      |     enabled-transports = ["akka.remote.netty.tcp"]
      |     netty.tcp {
      |         hostname = "127.0.0.1"
      |         port = 2559
      |     }
      |  }
      |}
    """.stripMargin
    
  val config = ConfigFactory.parseString(cfg)

  implicit val system = ActorSystem("SystemAgent", config)
  implicit val _ = system.dispatcher

  val seed_servers: Set[ActorSelection] = Set(
    system.actorSelection("akka.tcp://ClusterSystem@127.0.0.1:2553/user/server")
  )

  var members = seed_servers
  var seq = 0
  var logs = HashMap[Int, Any]()

  var key = 0
  val data = "user data "

  val agent = actor(new Act {

    whenStarting {
      println("Agent is starting...")
    }

    become {
      
      case PONG(_seq, _members) if _seq == seq =>
        members = _members.filterNot(_ == None) map {
          m => context.actorSelection(RootActorPath(m.get) / "user" / "server")
        }
        logs(_seq) match {
          case c@Command(_,_) =>
            sender ! c
          case q =>
            sender ! q
        }
        seq += 1

      case q@Query(_) =>
        logs += seq -> q
        members foreach { s =>
          s ! PING(seq)
        }

      case c@Command(_,_) =>
        logs += seq -> c
        members foreach { s =>
          s ! PING(seq)
        }

      case QueryOK(msg) =>
        println(s"query ok, data = $msg")
       
      case q: QueryFail =>
        println(s"query fail, reason = $q")

      case c: CommandOK =>
        println(s"command ok, reply = $c")
        
      case c: CommandFail =>
        key -= 1
        println(s"command fail, reply = $c")

      case x => //println(s"Agent discarding message: $x from sender $sender")
    }
  })

  //TODO : wrapper for client
  def get(key: Any) = ???

  //TODO : wrapper for client
  def put(key: Any, value: Any) = ???

  //client 'put key val' simulation
  system.scheduler.schedule(1000 milliseconds, 1000 milliseconds) {
    key += 1
    agent ! Command("key" + key, data + key)
  }

  //client 'get key' simulation
  system.scheduler.schedule(1500 milliseconds, 1000 milliseconds) {
    agent ! Query("key" + key)
  }

}
