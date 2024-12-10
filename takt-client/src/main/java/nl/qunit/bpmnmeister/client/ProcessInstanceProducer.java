package nl.qunit.bpmnmeister.client;

import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.StartCommandDTO;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class ProcessInstanceProducer {

  private final KafkaPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<String, StartCommandDTO> startCommandEmitter;

  public ProcessInstanceProducer(KafkaPropertiesHelper kafkaPropertiesHelper) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    startCommandEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                (Class<? extends Serializer<?>>) Serdes.String().serializer().getClass(),
                StartCommandSerializer.class));
  }

  public void startProcess(String processDefinitionId, VariablesDTO variables) {
    UUID processInstanceKey = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceKey,
            Constants.NONE_UUID,
            Constants.NONE,
            List.of(),
            List.of(),
            processDefinitionId,
            variables);
    startCommandEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC),
            processDefinitionId,
            startCommand));
  }
}
