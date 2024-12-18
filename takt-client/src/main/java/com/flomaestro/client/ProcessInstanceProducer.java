package com.flomaestro.client;

import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class ProcessInstanceProducer {

  private final KafkaPropertiesHelper kafkaPropertiesHelper;
  private final KafkaProducer<String, StartCommandDTO> startCommandEmitter;
  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;

  public ProcessInstanceProducer(KafkaPropertiesHelper kafkaPropertiesHelper) {
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;

    startCommandEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                (Class<? extends Serializer<?>>) Serdes.String().serializer().getClass(),
                StartCommandSerializer.class));

    processInstanceTriggerEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                (Class<? extends Serializer<?>>) Serdes.UUID().serializer().getClass(),
                ProcessInstanceTriggerSerializer.class));
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
