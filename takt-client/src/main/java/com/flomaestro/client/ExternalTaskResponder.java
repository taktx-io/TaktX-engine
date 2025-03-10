package com.flomaestro.client;

import com.flomaestro.client.serdes.ProcessInstanceTriggerSerializer;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import com.flomaestro.takt.util.TaktUUIDSerializer;
import java.io.IOException;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;

public class ExternalTaskResponder {

  private final KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter;
  private final String topicName;

  public ExternalTaskResponder(TaktPropertiesHelper taktPropertiesHelper) throws IOException {
    this.topicName = taktPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    this.responseEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));
  }

  public ExternalTaskInstanceResponder responderForExternalTaskTrigger(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return new ExternalTaskInstanceResponder(
        responseEmitter,
        topicName,
        externalTaskTriggerDTO.getProcessInstanceKey(),
        externalTaskTriggerDTO.getElementInstanceIdPath());
  }

}
