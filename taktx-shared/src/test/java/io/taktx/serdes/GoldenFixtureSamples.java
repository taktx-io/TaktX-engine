/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.IncidentInfoDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.NewDefinitionSignalSubscriptionDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import io.taktx.proto.FlowNodeInstanceEnvelope;
import io.taktx.proto.GlobalConfigurationMessage;
import io.taktx.proto.InstanceUpdateEnvelope;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.proto.ParsedDefinitionsMessage;
import io.taktx.proto.ProcessInstanceMessage;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.proto.SignalEnvelope;
import io.taktx.proto.SigningKeyMessage;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.variables.Variables;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class GoldenFixtureSamples {

  private GoldenFixtureSamples() {}

  static List<GoldenFixture<? extends MessageLite>> allFixtures() {
    return List.of(
        processInstanceTriggerFixture(),
        instanceUpdateFixture(),
        flowNodeInstanceFixture(),
        processInstanceFixture(),
        parsedDefinitionsFixture(),
        messageEventFixture(),
        signalFixture(),
        userTaskTriggerFixture(),
        dlqEnvelopeFixture(),
        globalConfigurationFixture(),
        signingKeyFixture());
  }

  static GoldenFixture<ProcessInstanceTriggerEnvelope> processInstanceTriggerFixture() {
    UUID processInstanceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID parentProcessInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    VariablesDTO variables = orderedVariables("count", 3L, "status", "ready", "approved", true);
    CommandTrustMetadataDTO trustMetadata =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT_AND_ED25519)
            .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
            .trusted(true)
            .userId("alice")
            .issuer("issuer-a")
            .signerKeyId("engine-key")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();

    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceId,
            parentProcessInstanceId,
            "start-event",
            null,
            new ProcessDefinitionKey("proc-a", 7),
            variables,
            true,
            linkedSet(
                new IoVariableMappingDTO("payload.total", "total"),
                new IoVariableMappingDTO("payload.customer", "customer")),
            "order-42",
            linkedSet("priority.high", "customer.vip"));
    startCommand.setCurrentTrustMetadata(trustMetadata);
    startCommand.setOriginTrustMetadata(trustMetadata);

    ProcessInstanceTriggerEnvelope message =
        ProcessInstanceTriggerProtoMapper.toProto(startCommand);
    return fixture(
        "process-instance-trigger.bin", message, ProcessInstanceTriggerEnvelope.parser());
  }

  static GoldenFixture<InstanceUpdateEnvelope> instanceUpdateFixture() {
    CommandTrustMetadataDTO currentTrust =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("alice")
            .issuer("issuer-a")
            .signerKeyId("engine-key-a")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();
    CommandTrustMetadataDTO originTrust =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT_AND_ED25519)
            .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
            .trusted(true)
            .userId("bob")
            .issuer("issuer-b")
            .signerKeyId("engine-key-b")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();

    ProcessInstanceUpdateDTO processUpdate =
        new ProcessInstanceUpdateDTO(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            List.of(10L, 20L),
            new ProcessDefinitionKey("order-process", 4),
            new IncidentInfoDTO(
                List.of(10L, 20L, 30L),
                "incident message",
                new String[] {"line 1", "line 2"},
                "instance-update-dlq:0:12:sha256:deadbeef"),
            sampleScope(),
            orderedVariables("status", "RUNNING", "attempt", 3L, "approved", true),
            1_713_000_000_000L,
            1_713_000_009_999L,
            "order-42",
            linkedSet("vip", "priority"));
    processUpdate.setCurrentTrustMetadata(currentTrust);
    processUpdate.setOriginTrustMetadata(originTrust);

    InstanceUpdateEnvelope message = InstanceUpdateProtoMapper.toProto(processUpdate);
    return fixture("instance-update.bin", message, InstanceUpdateEnvelope.parser());
  }

  static GoldenFixture<FlowNodeInstanceEnvelope> flowNodeInstanceFixture() {
    ReceiveTaskInstanceDTO receiveTask = new ReceiveTaskInstanceDTO();
    receiveTask.setState(ExecutionState.ACTIVE);
    receiveTask.setElementInstanceId(28L);
    receiveTask.setParentElementInstanceId(27L);
    receiveTask.setElementIndex(3);
    receiveTask.setElementId("receive-task");
    receiveTask.setPassedCnt(28);
    receiveTask.setIncident(false);
    receiveTask.setIteration(true);
    receiveTask.setNextIterationId(1028L);
    receiveTask.setInputElement(
        Variables.of(linkedVariableMap("input", "receive-task", "count", 28L)));
    receiveTask.setOutputElement(Variables.of(linkedVariableMap("result", true, "sequence", 29L)));
    receiveTask.setLoopCnt(3);
    receiveTask.setCorrelationKey("order-42");

    LinkedHashMap<MessageEventKeyDTO, Set<String>> messageEventKeys = new LinkedHashMap<>();
    messageEventKeys.put(
        new MessageEventKeyDTO("payment.received"), linkedSet("order-42", "order-43"));
    messageEventKeys.put(
        new MessageEventKeyDTO("payment.cancelled"), linkedSet("order-44", "order-45"));
    receiveTask.setMessageEventKeys(messageEventKeys);

    FlowNodeInstanceEnvelope message = FlowNodeInstanceProtoMapper.toProto(receiveTask);
    return fixture("flow-node-instance.bin", message, FlowNodeInstanceEnvelope.parser());
  }

  static GoldenFixture<ProcessInstanceMessage> processInstanceFixture() {
    UUID processInstanceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID parentProcessInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    ProcessInstanceDTO dto =
        new ProcessInstanceDTO(
            processInstanceId,
            parentProcessInstanceId,
            sampleScope(),
            List.of(10L, 20L),
            new ProcessDefinitionKey("order-process", 4),
            true,
            linkedSet(
                new IoVariableMappingDTO("payload.total", "total"),
                new IoVariableMappingDTO("payload.customerId", "customerId")),
            new IncidentInfoDTO(
                List.of(10L, 20L, 30L),
                "incident message",
                new String[] {"line 1", "line 2"},
                "process-instance-dlq:0:12:sha256:deadbeef"),
            "order-42",
            linkedSet("vip", "priority"));

    ProcessInstanceMessage message = ProcessInstanceProtoMapper.toProto(dto);
    return fixture("process-instance.bin", message, ProcessInstanceMessage.parser());
  }

  static GoldenFixture<ParsedDefinitionsMessage> parsedDefinitionsFixture() {
    ParsedDefinitionsMessage message =
        DefinitionsProtoMapper.toProto(DefinitionsProtoMapperTest.sampleParsedDefinitions());
    return fixture("parsed-definitions.bin", message, ParsedDefinitionsMessage.parser());
  }

  static GoldenFixture<MessageEventEnvelope> messageEventFixture() {
    MessageEventEnvelope message =
        MessageEventProtoMapper.toProto(
            new CorrelationMessageEventTriggerDTO(
                "payment.received",
                "order-42",
                orderedVariables("amount", 99L, "approved", true, "source", "golden")));
    return fixture("message-event.bin", message, MessageEventEnvelope.parser());
  }

  static GoldenFixture<SignalEnvelope> signalFixture() {
    SignalEnvelope message =
        SignalProtoMapper.toProto(
            new NewDefinitionSignalSubscriptionDTO(
                new ProcessDefinitionKey("signal-process", 3),
                "SignalStartEvent_1",
                "order-cancelled"));
    return fixture("signal.bin", message, SignalEnvelope.parser());
  }

  static GoldenFixture<UserTaskTriggerMessage> userTaskTriggerFixture() {
    UserTaskTriggerDTO dto =
        new UserTaskTriggerDTO(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new ProcessDefinitionKey("review", -1),
            "approve-order",
            List.of(1L, 2L, 3L),
            new io.taktx.dto.AssignmentDefinitionDTO("demo", "sales", "jane"),
            new TaskScheduleDTO("2026-05-19", "2026-05-20"),
            new PriorityDefinitionDTO("50"),
            orderedVariables("amount", 100L, "currency", "EUR", "approved", false));
    UserTaskTriggerMessage message = WorkerTriggerProtoMapper.toProto(dto);
    return fixture("user-task-trigger.bin", message, UserTaskTriggerMessage.parser());
  }

  static GoldenFixture<io.taktx.proto.DlqEnvelope> dlqEnvelopeFixture() {
    DlqEnvelope dto =
        new DlqEnvelope(
            "process-instance-trigger",
            new byte[] {1, 2, 3},
            new byte[] {4, 5, 6},
            linkedStringMap("tx-sig", "abc", "dlq-hint", "PROCESSOR_EXCEPTION"),
            DlqReasonCode.REPLAY_DETECTED,
            "duplicate audit id",
            DlqSeverity.CRITICAL,
            DlqCaptureStage.PROCESSOR,
            1_715_000_000_000L,
            "tenant.ns@host:8080",
            0,
            55L,
            99L,
            "sha256:abc",
            "ProcessInstanceDlqEntryDTO",
            3,
            "engine-v1",
            "fingerprint-1",
            "{\"summary\":true}",
            "{\"extra\":true}",
            DlqLineageDTO.builder()
                .sourceTopic("source-topic")
                .sourcePartition(1)
                .sourceOffset(2L)
                .sourceTimestampMs(3L)
                .sourceMessageHash("sha256:src")
                .sourceSignatureKeyId("worker-1")
                .sourceSignature("base64sig")
                .build(),
            "engine-a",
            "engine-key-1");
    io.taktx.proto.DlqEnvelope message = DlqProtoMapper.toProto(dto);
    return fixture("dlq-envelope.bin", message, io.taktx.proto.DlqEnvelope.parser());
  }

  static GoldenFixture<GlobalConfigurationMessage> globalConfigurationFixture() {
    GlobalConfigurationDTO dto =
        GlobalConfigurationDTO.builder()
            .signingEnabled(true)
            .engineRequiresAuthorization(true)
            .engineRequiresExternalTaskAuthorization(true)
            .engineRequiresUserTaskAuthorization(true)
            .trustedKeyIds(List.of("worker-a", "worker-b", "engine-key"))
            .dmnValidationMode(io.taktx.dto.DmnValidationMode.STRICT)
            .replayProtectionMode(ReplayProtectionMode.STRICT)
            .replayProtectionRetentionMs(123_456L)
            .build();
    GlobalConfigurationMessage message = ConfigurationProtoMapper.toProto(dto);
    return fixture("global-configuration.bin", message, GlobalConfigurationMessage.parser());
  }

  static GoldenFixture<SigningKeyMessage> signingKeyFixture() {
    SigningKeyDTO dto =
        SigningKeyDTO.builder()
            .keyId("platform-key")
            .publicKeyBase64("PUB")
            .algorithm("RSA")
            .createdAt(Instant.parse("2026-05-19T10:00:00Z"))
            .status(SigningKeyDTO.KeyStatus.TRUSTED)
            .role(KeyRole.PLATFORM)
            .registrationSignature("sig==")
            .build();
    SigningKeyMessage message = SigningKeyProtoMapper.toProto(dto);
    return fixture("signing-key.bin", message, SigningKeyMessage.parser());
  }

  private static ScopeDTO sampleScope() {
    TimerSubscriptionDTO timerSubscription = new TimerSubscriptionDTO();
    timerSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    timerSubscription.setElementId("timer-boundary-1");
    timerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            List.of(10L, 20L),
            "timer-boundary-1",
            TimeBucket.MINUTE));

    TimerSubscriptionDTO processLevelTimerSubscription = new TimerSubscriptionDTO();
    processLevelTimerSubscription.setSubScriptionType(SubScriptionType.STARTING);
    processLevelTimerSubscription.setElementId("process-level-timer-1");
    processLevelTimerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            List.of(),
            "process-level-timer-1",
            TimeBucket.MINUTE));

    MessageSubscriptionDTO messageSubscription = new MessageSubscriptionDTO();
    messageSubscription.setSubScriptionType(SubScriptionType.STARTING);
    messageSubscription.setElementId("message-catch-1");
    messageSubscription.setName("payment.received");
    messageSubscription.setCorrelationKey("order-42");

    SignalSubscriptionDTO signalSubscription = new SignalSubscriptionDTO();
    signalSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    signalSubscription.setElementId("signal-catch-1");
    signalSubscription.setName("cancel-order");

    LinkedHashMap<Long, List<io.taktx.dto.SubscriptionDTO>> subscriptionsByScope =
        new LinkedHashMap<>();
    subscriptionsByScope.put(-1L, List.of(messageSubscription, processLevelTimerSubscription));
    subscriptionsByScope.put(200L, List.of(timerSubscription, signalSubscription));

    SubscriptionsDTO subscriptions = new SubscriptionsDTO();
    subscriptions.setInstanceSubscriptions(subscriptionsByScope);

    LinkedHashMap<String, Long> outputFlowsCounter = new LinkedHashMap<>();
    outputFlowsCounter.put("gateway-a", 4L);
    outputFlowsCounter.put("gateway-b", 2L);

    return new ScopeDTO(ExecutionState.ACTIVE, 2, 1, 9L, outputFlowsCounter, subscriptions);
  }

  private static VariablesDTO orderedVariables(Object... keyValues) {
    LinkedHashMap<String, io.taktx.proto.VariableValue> variables = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      variables.put((String) keyValues[i], Variables.of(keyValues[i + 1]));
    }
    return VariablesDTO.ofVariableMap(variables);
  }

  private static LinkedHashMap<String, io.taktx.proto.VariableValue> linkedVariableMap(
      Object... keyValues) {
    LinkedHashMap<String, io.taktx.proto.VariableValue> values = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put((String) keyValues[i], Variables.of(keyValues[i + 1]));
    }
    return values;
  }

  private static LinkedHashMap<String, String> linkedStringMap(String... keyValues) {
    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put(keyValues[i], keyValues[i + 1]);
    }
    return values;
  }

  @SafeVarargs
  private static <T> LinkedHashSet<T> linkedSet(T... values) {
    return new LinkedHashSet<>(List.of(values));
  }

  private static <T extends MessageLite> GoldenFixture<T> fixture(
      String resourceName, T message, Parser<T> parser) {
    return new GoldenFixture<>(resourceName, message, parser, message.getSerializedSize());
  }

  record GoldenFixture<T extends MessageLite>(
      String resourceName, T message, Parser<T> parser, int maxSizeBytes) {

    byte[] bytes() {
      return message.toByteArray();
    }

    T parse(byte[] bytes) throws com.google.protobuf.InvalidProtocolBufferException {
      return parser.parseFrom(bytes);
    }
  }
}
