package nl.qunit.bpmnmeister.scheduler.kafka.producer;

import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaProducer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class ReplyProducer {

  KafkaProducer<String, Trigger> producer;

  public ReplyProducer(KafkaClientService kafkaClientService) {
    this.producer = kafkaClientService.getProducer("outgoing");
  }

  public void send(ProducerRecord<String, Trigger> producerRecord) {
    producer
        .runOnSendingThread(
            (Consumer<Producer<String, Trigger>>) client -> client.send(producerRecord))
        .await()
        .indefinitely();
  }
}
