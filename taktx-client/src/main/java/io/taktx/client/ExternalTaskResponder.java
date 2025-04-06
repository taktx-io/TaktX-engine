package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;

public class ExternalTaskResponder {

  private final KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter;
  private final String topicName;

  public ExternalTaskResponder(TaktPropertiesHelper taktPropertiesHelper) {
    this.topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.responseEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  public ExternalTaskInstanceResponder responderForExternalTaskTrigger(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return new ExternalTaskInstanceResponder(
        responseEmitter,
        topicName,
        externalTaskTriggerDTO.getProcessInstanceKey(),
        externalTaskTriggerDTO.getElementInstanceIdPath());
  }
}
