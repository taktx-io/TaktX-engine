/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.Topics;
import io.taktx.client.dlq.DlqReplayCommandBuilder;
import io.taktx.client.serdes.XmlDefinitionSerializer;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.KafkaConsumerUtil;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import io.taktx.serdes.DlqEnvelopeDtoDeserializer;
import io.taktx.serdes.DlqProtoMapper;
import io.taktx.serdes.DlqReplayResultDtoDeserializer;
import io.taktx.xml.BpmnParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class DlqReplayEndToEndIntegrationTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void malformedDefinitionsRecordCanBeCapturedAndReplayedEndToEnd() throws IOException {
    BpmnTestEngine engine = SingletonBpmnTestEngine.getInstance();
    String processId = "proto410-dlq-replay-" + UUID.randomUUID().toString().replace("-", "");
    String validXml = loadTaskSingleBpmn().replace("task-single", processId);
    ParsedDefinitionsDTO parsedDefinitions = BpmnParser.parse(validXml);

    ConcurrentLinkedQueue<ConsumerRecord<String, DlqEnvelope>> dlqRecords =
        new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<ConsumerRecord<String, DlqReplayResult>> replayResults =
        new ConcurrentLinkedQueue<>();

    KafkaConsumerUtil<String, DlqEnvelope> dlqConsumer =
        new KafkaConsumerUtil<>(
            "proto410-dlq-group-" + UUID.randomUUID(),
            prefixed(Topics.DLQ.getTopicName()),
            StringDeserializer.class.getName(),
            DlqEnvelopeDtoDeserializer.class.getName(),
            dlqRecords::add);
    KafkaConsumerUtil<String, DlqReplayResult> replayResultConsumer =
        new KafkaConsumerUtil<>(
            "proto410-dlq-replay-group-" + UUID.randomUUID(),
            prefixed(Topics.DLQ_REPLAY_RESULTS.getTopicName()),
            StringDeserializer.class.getName(),
            DlqReplayResultDtoDeserializer.class.getName(),
            replayResults::add);

    try {
      publishMalformedDefinitionsRecord();

      AtomicReference<DlqEnvelope> envelopeRef = new AtomicReference<>();
      Awaitility.await()
          .atMost(BpmnTestEngine.DEFAULT_DURATION)
          .untilAsserted(
              () -> {
                DlqEnvelope envelope =
                    dlqRecords.stream()
                        .map(ConsumerRecord::value)
                        .filter(
                            entry ->
                                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC
                                        .getTopicName()
                                        .equals(entry.getSourceTopic())
                                    && entry.getReasonCode() == DlqReasonCode.PROCESSOR_EXCEPTION)
                        .findFirst()
                        .orElse(null);
                assertThat(envelope).isNotNull();
                envelopeRef.set(envelope);
              });
      DlqEnvelope envelope = envelopeRef.get();

      byte[] correctedPayload = serializeDefinitions(validXml);
      DlqReplayCommand replayCommand =
          DlqReplayCommandBuilder.from(envelope)
              .operatorId("proto-4.10-it")
              .correctedKey(processId.getBytes(StandardCharsets.UTF_8))
              .correctedPayload(correctedPayload)
              .build();

      publishReplayCommand(replayCommand);

      AtomicReference<DlqReplayResult> replayResultRef = new AtomicReference<>();
      Awaitility.await()
          .atMost(BpmnTestEngine.DEFAULT_DURATION)
          .untilAsserted(
              () -> {
                DlqReplayResult replayResult =
                    replayResults.stream()
                        .map(ConsumerRecord::value)
                        .filter(
                            result ->
                                replayCommand.getDlqEntryRef().equals(result.getDlqEntryRef()))
                        .findFirst()
                        .orElse(null);
                assertThat(replayResult).isNotNull();
                replayResultRef.set(replayResult);
              });
      DlqReplayResult replayResult = replayResultRef.get();

      assertThat(replayResult.getStatus()).isEqualTo("SUCCESS");
      assertThat(replayResult.getReplaySignatureKeyId()).isNotBlank();
      assertThat(replayResult.getOutcomeText())
          .contains(prefixed(Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()));

      Awaitility.await()
          .atMost(BpmnTestEngine.DEFAULT_DURATION)
          .untilAsserted(
              () ->
                  assertThat(
                          engine
                              .getTaktClient()
                              .getProcessDefinitionByHash(
                                  processId, parsedDefinitions.getDefinitionsKey().getHash()))
                      .isPresent());

      UUID processInstanceId = engine.getTaktClient().startProcess(processId, VariablesDTO.empty());
      Awaitility.await()
          .atMost(BpmnTestEngine.DEFAULT_DURATION)
          .untilAsserted(
              () -> {
                assertThat(engine.getProcessInstance(processInstanceId)).isNotNull();
                assertThat(engine.getProcessInstance(processInstanceId).getScope().getState())
                    .isEqualTo(ExecutionState.COMPLETED);
              });
    } finally {
      dlqConsumer.stop();
      replayResultConsumer.stop();
    }
  }

  private static void publishMalformedDefinitionsRecord() {
    Properties props = producerProps();
    try (KafkaProducer<String, XmlDefinitionsDTO> producer =
        new KafkaProducer<>(props, new StringSerializer(), new XmlDefinitionSerializer())) {
      producer.send(
          new ProducerRecord<>(
              prefixed(Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
              "broken-definition",
              new XmlDefinitionsDTO("NOT_VALID_XML <<<")));
      producer.flush();
    }
  }

  private static void publishReplayCommand(DlqReplayCommand replayCommand) {
    Properties props = producerProps();
    try (KafkaProducer<String, byte[]> producer =
        new KafkaProducer<>(props, new StringSerializer(), new ByteArraySerializer())) {
      producer.send(
          new ProducerRecord<>(
              prefixed(Topics.DLQ_REPLAY.getTopicName()),
              replayCommand.getDlqEntryRef(),
              DlqProtoMapper.toProto(replayCommand).toByteArray()));
      producer.flush();
    }
  }

  private static byte[] serializeDefinitions(String xml) {
    try (XmlDefinitionSerializer serializer = new XmlDefinitionSerializer()) {
      return serializer.serialize(null, new XmlDefinitionsDTO(xml));
    }
  }

  private static Properties producerProps() {
    Properties props = new Properties();
    props.put(
        "bootstrap.servers",
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class));
    props.put("acks", "all");
    return props;
  }

  private static String loadTaskSingleBpmn() throws IOException {
    try (InputStream inputStream =
        DlqReplayEndToEndIntegrationTest.class.getResourceAsStream("/bpmn/task-single.bpmn")) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing test resource: /bpmn/task-single.bpmn");
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String prefixed(String topicName) {
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", "default");
    return new io.taktx.util.TaktPropertiesHelper(props).getPrefixedTopicName(topicName);
  }
}
