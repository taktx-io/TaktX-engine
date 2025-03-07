package com.flomaestro.client;

import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import com.flomaestro.takt.util.TaktUUIDSerializer;
import java.io.IOException;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ProcessInstanceProducer {

  private final TaktPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;

  public ProcessInstanceProducer(TaktPropertiesHelper kafkaPropertiesHelper) throws IOException {
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
}
