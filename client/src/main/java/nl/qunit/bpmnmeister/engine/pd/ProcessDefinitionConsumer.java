package nl.qunit.bpmnmeister.engine.pd;

import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


@ApplicationScoped
public class ProcessDefinitionConsumer {
  @Inject
  Deployer deployer;

  @Incoming("process-definition-parsed-incoming")
  public void consumeParsedProcessDefinition(ProcessDefinition processDefinition) {
    String receivedProcessDefinitionId = processDefinition.getDefinitions().getProcessDefinitionId();
    String generation = processDefinition.getDefinitions().getGeneration();
    Map<String, String> genMap = deployer.getDefinitionMap().getOrDefault(receivedProcessDefinitionId, new HashMap<>());
    String filenameMatchingReceivedProcessDefinition = genMap.get(generation);
    if (filenameMatchingReceivedProcessDefinition != null) {

//      Properties propserties = new Properties();
//      KafkaConsumer<String, String> consumer = new KafkaConsumer<>(propserties);
//
//      // Create consumer for this process definition and generation
//      consumer.subscribe(Collections.singletonList("my-topic"));
    }
  }
}
