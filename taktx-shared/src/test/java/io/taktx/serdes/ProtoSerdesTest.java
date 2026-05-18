/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import io.taktx.proto.AbortTriggerMessage;
import io.taktx.proto.CleanupPolicy;
import io.taktx.proto.ConfigurationEventMessage;
import io.taktx.proto.ConfigurationEventType;
import io.taktx.proto.DefinitionMessageEventTriggerMessage;
import io.taktx.proto.DefinitionsKeyMessage;
import io.taktx.proto.DefinitionsTriggerEnvelope;
import io.taktx.proto.DlqCaptureStage;
import io.taktx.proto.DlqEnvelope;
import io.taktx.proto.DlqReasonCode;
import io.taktx.proto.DlqReplayCommand;
import io.taktx.proto.DlqReplayResult;
import io.taktx.proto.DmnDefinitionMessage;
import io.taktx.proto.DmnDefinitionStateEnum;
import io.taktx.proto.DmnValidationMode;
import io.taktx.proto.ExecutionState;
import io.taktx.proto.FlowNodeInstanceEnvelope;
import io.taktx.proto.FlowNodeInstanceUpdateMessage;
import io.taktx.proto.GlobalConfigurationMessage;
import io.taktx.proto.InstanceUpdateEnvelope;
import io.taktx.proto.KeyRole;
import io.taktx.proto.KeyStatus;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.proto.MessageScheduleEnvelope;
import io.taktx.proto.OneTimeScheduleMessage;
import io.taktx.proto.ParsedDefinitionsMessage;
import io.taktx.proto.ParsedDmnDefinitionsMessage;
import io.taktx.proto.ProcessDefinitionMessage;
import io.taktx.proto.ProcessDefinitionStateEnum;
import io.taktx.proto.ProcessInstanceMessage;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.proto.ReplayProtectionMode;
import io.taktx.proto.SchedulableMessageEnvelope;
import io.taktx.proto.SignalEnvelope;
import io.taktx.proto.SignalMessage;
import io.taktx.proto.SigningKeyMessage;
import io.taktx.proto.StartEventInstanceMessage;
import io.taktx.proto.TaskInstanceMessage;
import io.taktx.proto.TopicMetaMessage;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.proto.XmlDefinitionsMessage;
import java.util.stream.Stream;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtoSerdesTest {

  private static final String TOPIC = "proto-serdes-test";
  private static final ProtoSerializer<MessageLite> SERIALIZER = new ProtoSerializer<>();

  @ParameterizedTest(name = "{0} round-trips through proto serdes")
  @MethodSource("roundTripCases")
  void serializerAndDeserializer_roundTripEachTopLevelMessage(
      String name, MessageLite message, Deserializer<? extends MessageLite> deserializer) {
    byte[] bytes = SERIALIZER.serialize(TOPIC, message);

    assertThat(bytes).isEqualTo(message.toByteArray());
    assertThat(deserialize(deserializer, bytes)).isEqualTo(message);
  }

  @ParameterizedTest(name = "{0} deserializer returns null for tombstones")
  @MethodSource("roundTripCases")
  void deserializer_returnsNullForTombstones(
      String name, MessageLite message, Deserializer<? extends MessageLite> deserializer) {
    assertThat(message).as("sample payload for %s", name).isNotNull();
    assertThat(deserialize(deserializer, null)).isNull();
  }

  @ParameterizedTest(name = "{0} serializer returns null for tombstones")
  @MethodSource("roundTripCases")
  void serializer_returnsNullForTombstones(
      String name, MessageLite message, Deserializer<? extends MessageLite> deserializer) {
    assertThat(message).as("sample payload for %s", name).isNotNull();
    assertThat(deserializer).as("deserializer for %s", name).isNotNull();
    assertThat(SERIALIZER.serialize(TOPIC, null)).isNull();
  }

  @ParameterizedTest(name = "{0} wraps invalid proto bytes in SerializationException")
  @MethodSource("roundTripCases")
  void corruptedBytes_surfaceInvalidProtocolBufferCause(
      String name, MessageLite message, Deserializer<? extends MessageLite> deserializer) {
    byte[] corrupt = {0x0A, 0x02, 0x01};

    assertThat(message).as("sample payload for %s", name).isNotNull();
    assertThatThrownBy(() -> deserialize(deserializer, corrupt))
        .isInstanceOf(SerializationException.class)
        .hasCauseInstanceOf(InvalidProtocolBufferException.class);
  }

  private static Stream<Arguments> roundTripCases() {
    return Stream.of(
        Arguments.of(
            "processInstanceTrigger",
            ProcessInstanceTriggerEnvelope.newBuilder()
                .setAbort(AbortTriggerMessage.newBuilder().addElementInstanceIdPath(7L).build())
                .build(),
            new ProcessInstanceTriggerDeserializer()),
        Arguments.of(
            "instanceUpdate",
            InstanceUpdateEnvelope.newBuilder()
                .setFlowNode(
                    FlowNodeInstanceUpdateMessage.newBuilder()
                        .setFlowNodeInstance(
                            FlowNodeInstanceEnvelope.newBuilder()
                                .setTask(
                                    TaskInstanceMessage.newBuilder()
                                        .setElementId("task-a")
                                        .setState(ExecutionState.EXECUTION_STATE_ACTIVE)
                                        .build())
                                .build())
                        .setProcessTime(42L)
                        .build())
                .build(),
            new InstanceUpdateDeserializer()),
        Arguments.of(
            "flowNodeInstance",
            FlowNodeInstanceEnvelope.newBuilder()
                .setStartEvent(
                    StartEventInstanceMessage.newBuilder()
                        .setElementId("start")
                        .setState(ExecutionState.EXECUTION_STATE_ACTIVE)
                        .build())
                .build(),
            new FlowNodeInstanceDeserializer()),
        Arguments.of(
            "processInstance",
            ProcessInstanceMessage.newBuilder().setBusinessKey("order-123").build(),
            new ProcessInstanceDeserializer()),
        Arguments.of(
            "parsedDefinitions",
            ParsedDefinitionsMessage.newBuilder()
                .setDefinitionsKey(
                    DefinitionsKeyMessage.newBuilder()
                        .setProcessDefinitionId("proc-a")
                        .setHash("hash-1")
                        .build())
                .build(),
            new ParsedDefinitionsDeserializer()),
        Arguments.of(
            "definitionsTrigger",
            DefinitionsTriggerEnvelope.newBuilder()
                .setXmlDefs(XmlDefinitionsMessage.newBuilder().setXml("<bpmn/>").build())
                .build(),
            new DefinitionsTriggerDeserializer()),
        Arguments.of(
            "messageEvent",
            MessageEventEnvelope.newBuilder()
                .setDefTrigger(
                    DefinitionMessageEventTriggerMessage.newBuilder()
                        .setMessageName("payment.received")
                        .build())
                .build(),
            new MessageEventDeserializer()),
        Arguments.of(
            "signal",
            SignalEnvelope.newBuilder()
                .setSignalMsg(SignalMessage.newBuilder().setSignalName("order-cancelled").build())
                .build(),
            new SignalDeserializer()),
        Arguments.of(
            "userTaskTrigger",
            UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-1").build(),
            new UserTaskTriggerDeserializer()),
        Arguments.of(
            "messageSchedule",
            MessageScheduleEnvelope.newBuilder()
                .setOneTime(
                    OneTimeScheduleMessage.newBuilder()
                        .setMessage(
                            SchedulableMessageEnvelope.newBuilder()
                                .setProcessInstanceTrigger(
                                    ProcessInstanceTriggerEnvelope.newBuilder()
                                        .setAbort(AbortTriggerMessage.newBuilder().build())
                                        .build())
                                .build())
                        .setWhen(123L)
                        .build())
                .build(),
            new MessageScheduleDeserializer()),
        Arguments.of(
            "globalConfiguration",
            GlobalConfigurationMessage.newBuilder()
                .setSigningEnabled(true)
                .setEngineRequiresAuthorization(true)
                .setDmnValidationMode(DmnValidationMode.DMN_VALIDATION_STRICT)
                .setReplayProtectionMode(ReplayProtectionMode.COMPAT)
                .setReplayProtectionRetentionMs(30_000L)
                .build(),
            new GlobalConfigurationDeserializer()),
        Arguments.of(
            "configurationEvent",
            ConfigurationEventMessage.newBuilder()
                .setEventType(ConfigurationEventType.CONFIGURATION_UPDATE)
                .setConfiguration(
                    GlobalConfigurationMessage.newBuilder().setSigningEnabled(true).build())
                .setTimestampMs(99L)
                .build(),
            new ConfigurationEventDeserializer()),
        Arguments.of(
            "topicMeta",
            TopicMetaMessage.newBuilder()
                .setTopicName("io.taktx.events")
                .setCleanupPolicy(CleanupPolicy.CLEANUP_POLICY_COMPACT)
                .setMessageId("meta-1")
                .build(),
            new TopicMetaDeserializer()),
        Arguments.of(
            "dlqEnvelope",
            DlqEnvelope.newBuilder()
                .setSourceTopic("process-instance-trigger")
                .setReasonCode(DlqReasonCode.DLQ_REASON_UNKNOWN)
                .setCaptureStage(DlqCaptureStage.DLQ_CAPTURE_PROCESSOR)
                .build(),
            new DlqEnvelopeDeserializer()),
        Arguments.of(
            "dlqReplayCommand",
            DlqReplayCommand.newBuilder()
                .setDlqEntryRef("dlq-1")
                .setDestinationTopic("replay-topic")
                .build(),
            new DlqReplayCommandDeserializer()),
        Arguments.of(
            "dlqReplayResult",
            DlqReplayResult.newBuilder().setDlqEntryRef("dlq-1").setStatus("SUCCESS").build(),
            new DlqReplayResultDeserializer()),
        Arguments.of(
            "signingKey",
            SigningKeyMessage.newBuilder()
                .setKeyId("key-1")
                .setPublicKeyBase64("PUB")
                .setStatus(KeyStatus.KEY_STATUS_ACTIVE)
                .setRole(KeyRole.KEY_ROLE_ENGINE)
                .build(),
            new SigningKeyDeserializer()),
        Arguments.of(
            "dmnDefinition",
            DmnDefinitionMessage.newBuilder()
                .setDefinitions(
                    ParsedDmnDefinitionsMessage.newBuilder().setName("eligibility").build())
                .setVersion(1)
                .setState(DmnDefinitionStateEnum.DMN_DEFINITION_STATE_ACTIVE)
                .build(),
            new DmnDefinitionDeserializer()),
        Arguments.of(
            "processDefinition",
            ProcessDefinitionMessage.newBuilder()
                .setDefinitions(
                    ParsedDefinitionsMessage.newBuilder()
                        .setDefinitionsKey(
                            DefinitionsKeyMessage.newBuilder()
                                .setProcessDefinitionId("proc-b")
                                .setHash("hash-2")
                                .build())
                        .build())
                .setVersion(3)
                .setState(ProcessDefinitionStateEnum.PROCESS_DEFINITION_STATE_ACTIVE)
                .build(),
            new ProcessDefinitionDeserializer()));
  }

  @SuppressWarnings("unchecked")
  private static MessageLite deserialize(
      Deserializer<? extends MessageLite> deserializer, byte[] data) {
    return ((Deserializer<MessageLite>) deserializer).deserialize(TOPIC, data);
  }
}
