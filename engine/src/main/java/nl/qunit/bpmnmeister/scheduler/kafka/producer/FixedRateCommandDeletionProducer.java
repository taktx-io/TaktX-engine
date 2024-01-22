package nl.qunit.bpmnmeister.scheduler.kafka.producer;

import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaProducer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;

import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class FixedRateCommandDeletionProducer {

  KafkaProducer<ScheduleKey, Void> producer;

  public FixedRateCommandDeletionProducer(KafkaClientService kafkaClientService) {
    this.producer = kafkaClientService.getProducer("fixed-rate-out");
  }

  public void send(ProducerRecord<ScheduleKey, Void> producerRecord) {
    producer
        .runOnSendingThread(
            (Consumer<Producer<ScheduleKey, Void>>) client -> client.send(producerRecord))
        .await()
        .indefinitely();
  }
}
