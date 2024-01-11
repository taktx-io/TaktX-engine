package nl.qunit.bpmnmeister.engine.pd;

import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ProcessDefinitionConsumer implements KafkaConsumerRebalanceListener {

  @Incoming("process-definition-parsed-incoming")
  public void consumeParsedProcessDefinition(ProcessDefinition processDefinition) {
    System.out.println("Received process definition: " + processDefinition);

    // Checck if the process definition id, with matching hashcode is present in this client
    // instance. If so, subscribe to the topics corresponding to the process definition id and version.
    // If not, ignore the message, maybe another instance of the client is able to handle it.
  }

}
