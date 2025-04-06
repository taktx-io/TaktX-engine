package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.XmlDefinitionSerializer;
import io.taktx.dto.v_1_0_0.ParsedDefinitionsDTO;
import io.taktx.dto.v_1_0_0.XmlDefinitionsDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.xml.BpmnParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

@Slf4j
public class ProcessDefinitionDeployer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final KafkaProducer<String, XmlDefinitionsDTO> xmlEmitter;

  ProcessDefinitionDeployer(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.xmlEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, XmlDefinitionSerializer.class));
  }

  public ParsedDefinitionsDTO deploy(String xml) {
    log.info("Deploying XML {}", xml);
    ParsedDefinitionsDTO definitions = BpmnParser.parse(xml);
    xmlEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
            definitions.getDefinitionsKey().getProcessDefinitionId(),
            new XmlDefinitionsDTO(xml)));
    return definitions;
  }
}
