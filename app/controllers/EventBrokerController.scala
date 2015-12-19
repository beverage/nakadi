package controllers

import javax.inject.Inject

import akka.actor.{Props, ActorSystem}
import models.CommonTypes.{PartitionOffset, PartitionName, TopicName}
import models.Metrics
import play.api.libs.json.{Writes, Json}
import play.api.mvc.{Results, Codec, Action, Controller}

import scala.concurrent.ExecutionContext
import java.util
import java.util.concurrent.TimeUnit
import java.util.{Properties, UUID}

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import com.typesafe.config.ConfigFactory
import models._
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}
import play.api.http.Writeable
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee._
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}
import scala.util.Try

// define Kafka Simple Consuming Actor commands
object ActorCommands extends Enumeration {
  val Start, Next, Shutdown = Value
}
import controllers.ActorCommands._

class EventBrokerController @Inject() ()
                                   (implicit ec: ExecutionContext) extends Controller {

  implicit val defaultCodec = Codec.utf_8

  val getMetrics = Action { result =>
    Ok( Json.toJson(Metrics(application_name = "Nakadi Event Bus")) )
      .as(JSON)
  }

  def getTopics = withConsumer { consumer =>
    consumer.listTopics().asScala.keys.map(Topic.apply)
  }

  def getPartitionsForTopic(topic: String) = play.mvc.Results.TODO
  def getEventsFromTopic(topic: String) = play.mvc.Results.TODO

  def postEventsIntoTopic(topic: String) = play.mvc.Results.TODO

  val actorSystem = ActorSystem.create("nakadi")

  def getEventsFromPartition(topic: TopicName,
                             partition: PartitionName,
                             start_from: PartitionOffset,
                             stream_limit: Option[Int] = None
                            ) = Action.async {
    val actor = actorSystem.actorOf(
      Props(classOf[KafkaConsumerActor], topic, partition, start_from, stream_limit.getOrElse(0)))
    implicit val timeout = akka.util.Timeout(5, TimeUnit.SECONDS)
    akka.pattern.ask(actor, Start).mapTo[Enumerator[Event]].map(
      evt => Results.Ok.chunked(evt.map(Json.toJson(_))))
  }

  def getTopicPartitions(topic: String) = withConsumer { consumer =>
    consumer.partitionsFor(topic).asScala.map { (partitionInfo: PartitionInfo) =>
      Partition(name = partitionInfo.partition().toString,
                oldestAvailableOffset = ???,
                newestAvailableOffset = ???)
    }
  }

  private def withConsumer[A](f: KafkaConsumer[_, _] => A)(implicit w: Writes[A]): Action[AnyContent] = {
    Action {
      val consumer = KafkaClient()
      try {
        Results.Ok {
          Json.toJson {
            f(consumer)
          }
        }
      } finally {
        Try(consumer.close())
      }
    }
  }

}

object KafkaClient {
  def apply() = new KafkaConsumer[String, String]({
    val set = ConfigFactory.load().getConfig("kafka.consumer").entrySet().asScala
    val props = set.foldLeft(new Properties) {
      (p, item) =>
        val key = item.getKey
        val value = item.getValue.unwrapped().toString
        require(p.getProperty(key) == null, s"key ${item.getKey} exists more than once")
        p.setProperty(key, value)
        p
    }
    props.setProperty("group.id", UUID.randomUUID().toString)
    props
  })
}

class KafkaConsumerActor(topic: String, partition: String, cursor: String, streamLimit: Int) extends Actor {

  lazy val consumer = KafkaClient()
  val topicPartition = new TopicPartition(topic, partition.toInt)
  @volatile var channel: Channel[Event] = null
  var remaining = streamLimit

  override def receive: Receive = {
    case Start =>
      println("Got Start signal")

      consumer.assign(util.Arrays.asList(topicPartition))
      consumer.seek(topicPartition, cursor.toLong)
      sender ! Concurrent.unicast((c: Channel[Event]) => {
        this.channel = c
        self ! Next
      }).onDoneEnumerating(self ! Shutdown)
    case Next =>
      println("Got Next signal")
      // annoyingly, poll will block more or less forever if kafka isn't running
      val records = blocking(consumer.poll(500).asScala)
      println(s"got ${records.size} event from the queue")
      if (records.isEmpty) {
        self ! Next
      } else {
        val toStream = if (streamLimit == 0) records else records.take(math.min(remaining, records.size))
        toStream.foreach { (r: ConsumerRecord[String, String]) =>
          Json.fromJson[Event](Json.parse(r.value())).asOpt.fold {
            println(s"Could not parse ${r.value()}")
          } {
            channel.push(_)
          }
        }
        remaining -= toStream.size
        assert(remaining >= 0, remaining)
        if (streamLimit == 0 || remaining > 0) self ! Next
        else {
          channel.eofAndEnd()
          self ! Shutdown
        }
      }
    case Shutdown =>
      println("Got Shutdown signal")
      self ! PoisonPill
      Try(consumer.close())
  }
}