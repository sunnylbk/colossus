package colossus

import testkit._
import service._
import core._

import akka.actor._
import akka.testkit.TestProbe

import akka.util.ByteString
import java.net.InetSocketAddress

//sometimes these tests randomly fail when run with all the other tests

class AsyncClientSpec extends ColossusSpec {
  import ConnectionCommand._
  import ConnectionEvent._

  
  def createService()(implicit io: IOSystem) = {
    import protocols.telnet._
    import service._
    Service.become[Telnet]("test", TEST_PORT) {
      case TelnetCommand(c :: tail) => {
        println("HERE")
        TelnetReply(c)
      }
    }
  }

  def withClient(f: (ServerRef, ActorRef, TestProbe) => Any) {
    withIOSystem{implicit io => 
      val server = createService()
      waitForServer(server)
      val client = ActorHandler(new InetSocketAddress("localhost", TEST_PORT))
      val p2 = TestProbe()
      client ! ActorHandler.RegisterListener(p2.ref)
      p2.expectMsgType[Connected]
      f(server, client, p2)
    }
  }

      
    

  "Async Client" must {
    "connect to a server" in {
      withClient{case (server, client, probe) => 
        end(server)
      }
    }


    "teorminates on disconnect" in {
      withClient{case (s,c,p) => 
        p.watch(c)
        end(s)
        p.expectMsgType[ConnectionTerminated]
        p.expectTerminated(c)
      }
    }

    "write and read some data" in {
      withClient{case (s, c, p) => 
        val bytes = ByteString("abcdefg\r\n")
        c ! Write(bytes, AckLevel.AckNone)
        p.expectMsg(ReceivedData(bytes))
        end(s)
      }
    }

    "receive ack on successful write" in {
      withClient{ case (s, c, p) => 
        c ! Write(ByteString("abc"), AckLevel.AckSuccess)
        p.expectMsg(WriteAck(WriteStatus.Complete))
        end(s)
      }
    }
      
  }
}

