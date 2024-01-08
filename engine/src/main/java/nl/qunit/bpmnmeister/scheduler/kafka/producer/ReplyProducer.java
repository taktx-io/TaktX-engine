package nl.qunit.bpmnmeister.scheduler.kafka.producer;

import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class ReplyProducer {

  KafkaProducer<String, ProcessInstanceTrigger> producer;

  @Inject
  public ReplyProducer(KafkaClientService kafkaClientService) {
    this.producer = kafkaClientService.getProducer("outgoing");
  }

  public void send(ProducerRecord<String, ProcessInstanceTrigger> producerRecord) {
    producer
        .runOnSendingThread(
            (Consumer<Producer<String, ProcessInstanceTrigger>>)
                client -> client.send(producerRecord))
        .await()
        .indefinitely();
  }
}
