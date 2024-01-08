package nl.qunit.bpmnmeister.scheduler.kafka.producer;

import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaProducer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class OneTimeCommandDeletionProducer {

  KafkaProducer<String, Void> producer;

  public OneTimeCommandDeletionProducer(KafkaClientService kafkaClientService) {
    this.producer = kafkaClientService.getProducer("one-time-out");
  }

  public void send(ProducerRecord<String, Void> producerRecord) {
    producer
        .runOnSendingThread(
            (Consumer<Producer<String, Void>>) client -> client.send(producerRecord))
        .await()
        .indefinitely();
  }
}
