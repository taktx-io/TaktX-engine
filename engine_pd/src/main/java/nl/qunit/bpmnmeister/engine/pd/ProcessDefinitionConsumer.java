package nl.qunit.bpmnmeister.engine.pd;

import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
@RequiredArgsConstructor
public class ProcessDefinitionConsumer {
  private static final Logger LOG = Logger.getLogger(ProcessDefinitionConsumer.class);

  private final BpmnParser bpmnParser;

  private Map<String, Map<String, ProcessDefinition>> processDefinitions = new HashMap<>();

  @Incoming("process-definition-xml-incoming")
  @Outgoing("process-definition-parsed-outgoing")
  public OutgoingKafkaRecord<String, ProcessDefinition> consumeXml(
      ConsumerRecord<String, String> xmlRecord) {
    LOG.info("Received process definition XML: " + xmlRecord.value());
    try {
      Definitions definitions = bpmnParser.parse(xmlRecord.value());

      Map<String, ProcessDefinition> hashCodeMap =
          processDefinitions.computeIfAbsent(xmlRecord.key(), k -> new HashMap<>());
      if (!hashCodeMap.containsKey(definitions.getHash())) {
        ProcessDefinition processDefinition =
            ProcessDefinition.builder()
                .definitions(definitions)
                .version(hashCodeMap.size() + 1)
                .build();
        hashCodeMap.put(definitions.getHash(), processDefinition);
        return KafkaRecord.of(definitions.getProcessDefinitionId(), processDefinition);
      } else {
        LOG.info("Process definition already exists");
      }
      LOG.info("Parsed process definition: " + definitions);
    } catch (JAXBException e) {
      LOG.error("Failed to parse process definition XML", e);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("No sha256 algorithm", e);
    }
    return null;
  }

  @Incoming("process-definition-parsed-incoming")
  public void consumeParsed(ConsumerRecord<String, ProcessDefinition> processDefinitionRecord) {
    LOG.info(
        "Received process definition record: "
            + processDefinitionRecord.key()
            + " "
            + processDefinitionRecord.value());
    ProcessDefinition previous =
        processDefinitions
            .computeIfAbsent(processDefinitionRecord.key(), k -> new HashMap<>())
            .put(
                processDefinitionRecord.value().getDefinitions().getHash(),
                processDefinitionRecord.value());
    if (previous != null) {
      LOG.info("Process definition already exists");
    }
  }
}
