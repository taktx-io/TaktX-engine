package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.v_1_0_0.ProcessDefinitionKey;
import io.taktx.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import io.taktx.dto.v_1_0_0.StartCommandDTO;
import io.taktx.dto.v_1_0_0.TerminateTriggerDTO;
import io.taktx.dto.v_1_0_0.VariablesDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ProcessInstanceProducer {

  private final TaktPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;

  public ProcessInstanceProducer(TaktPropertiesHelper kafkaPropertiesHelper) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    processInstanceTriggerEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  public UUID startProcess(String processDefinitionId, VariablesDTO variables) {
    UUID processInstanceKey = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceKey,
            null,
            null,
            new ProcessDefinitionKey(processDefinitionId),
            variables);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceKey,
            startCommand));
    return processInstanceKey;
  }

  public void terminateProcessInstance(UUID processInstanceKey) {
    terminateElementInstance(processInstanceKey, List.of());
  }

  public void terminateElementInstance(UUID processInstanceKey, List<Long> elementInstanceIdPath) {
    TerminateTriggerDTO terminateTrigger =
        new TerminateTriggerDTO(processInstanceKey, elementInstanceIdPath);
    processInstanceTriggerEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            processInstanceKey,
            terminateTrigger));
  }
}
