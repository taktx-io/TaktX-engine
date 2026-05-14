/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.CleanupPolicy;
import io.taktx.dto.Constants;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.dlq.DlqPublisher;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.engine.license.LicenseManager;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.security.AuthorizationTokenException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DynamicTopicManagerTest {

  private static final String LOCAL_PREFIX = "acme.prod.";
  private static final String ACTUAL_TOPIC = LOCAL_PREFIX + "topic-meta-actual";
  private static final String REQUESTED_TOPIC = LOCAL_PREFIX + "topic-meta-requested";
  private static final String DLQ_TOPIC = LOCAL_PREFIX + "dlq";

  private AdminClient adminClient;
  private EngineAuthorizationService engineAuthorizationService;
  private KafkaProducer<String, TopicMetaDTO> topicMetaProducer;
  private KafkaProducer<String, DlqEnvelope> dlqProducer;
  private DlqPublisher dlqPublisher;
  private DynamicTopicManager dynamicTopicManager;

  @BeforeEach
  void setUp() throws Exception {
    adminClient = mock(AdminClient.class);
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    KafkaClientsConfig kafkaClientsConfig = mock(KafkaClientsConfig.class);
    LicenseManager licenseManager = mock(LicenseManager.class);
    engineAuthorizationService = mock(EngineAuthorizationService.class);
    topicMetaProducer = mock(KafkaProducer.class);
    dlqProducer = mock(KafkaProducer.class);
    dlqPublisher = mock(DlqPublisher.class);

    when(taktConfiguration.getPrefixed(anyString()))
        .thenAnswer(invocation -> LOCAL_PREFIX + invocation.getArgument(0, String.class));
    when(taktConfiguration.getTenantId()).thenReturn("tenant");
    when(taktConfiguration.getNamespace()).thenReturn("namespace");
    when(taktConfiguration.getHost()).thenReturn("host");
    when(taktConfiguration.getPort()).thenReturn(8080);
    when(licenseManager.getPartitionBudget()).thenReturn(Integer.MAX_VALUE);

    RequestedTopicValidator requestedTopicValidator =
        new RequestedTopicValidator(taktConfiguration);
    dynamicTopicManager =
        new DynamicTopicManager(
            adminClient,
            taktConfiguration,
            kafkaClientsConfig,
            licenseManager,
            engineAuthorizationService,
            requestedTopicValidator,
            dlqPublisher);

    setPrivateField(dynamicTopicManager, "topicMetaProducer", topicMetaProducer);
    setPrivateField(dynamicTopicManager, "dlqProducer", dlqProducer);
    setPrivateField(dynamicTopicManager, "cachedActualTopicName", ACTUAL_TOPIC);
    setPrivateField(dynamicTopicManager, "cachedRequestedTopicName", REQUESTED_TOPIC);
    setPrivateField(dynamicTopicManager, "cachedDlqTopicName", DLQ_TOPIC);
  }

  @Test
  void validExternalTaskTopicRequest_createsTopicAndCachesMetadata() throws Exception {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
    when(adminClient.createTopics(anyList())).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    dynamicTopicManager.processRequestedTopic(topicName, topicMeta);

    assertThat(
            dynamicTopicManager.topicExists(
                Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker"))
        .isTrue();
    assertThat(cachedRequestTopicMetaMap()).containsEntry(topicName, topicMeta);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<NewTopic>> topicsCaptor = ArgumentCaptor.forClass(List.class);
    verify(adminClient).createTopics(topicsCaptor.capture());
    assertThat(topicsCaptor.getValue())
        .singleElement()
        .extracting(NewTopic::name, NewTopic::numPartitions, NewTopic::replicationFactor)
        .containsExactly(topicName, 3, (short) 1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, TopicMetaDTO>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(topicMetaProducer).send(recordCaptor.capture(), any());
    assertThat(recordCaptor.getValue().topic()).isEqualTo(ACTUAL_TOPIC);
    assertThat(recordCaptor.getValue().key()).isEqualTo(topicName);
    assertThat(recordCaptor.getValue().value()).isEqualTo(topicMeta);
  }

  @Test
  void concurrentCreateRace_topicExistsExceptionIsTreatedAsIdempotentSuccess() throws Exception {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
    @SuppressWarnings("unchecked")
    KafkaFuture<Void> failedFuture = mock(KafkaFuture.class);
    when(adminClient.createTopics(anyList())).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(failedFuture);
    when(failedFuture.get())
        .thenThrow(new ExecutionException(new TopicExistsException("already created elsewhere")));
    mockDescribeTopics(topicName, 6, (short) 2);
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    dynamicTopicManager.processRequestedTopic(topicName, topicMeta);

    assertThat(
            dynamicTopicManager.topicExists(
                Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker"))
        .isTrue();
    assertThat(cachedRequestTopicMetaMap()).containsEntry(topicName, topicMeta);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, TopicMetaDTO>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(topicMetaProducer).send(recordCaptor.capture(), any());
    assertThat(recordCaptor.getValue().topic()).isEqualTo(ACTUAL_TOPIC);
    assertThat(recordCaptor.getValue().key()).isEqualTo(topicName);
    assertThat(recordCaptor.getValue().value())
        .extracting(
            TopicMetaDTO::getTopicName,
            TopicMetaDTO::getNrPartitions,
            TopicMetaDTO::getCleanupPolicy,
            TopicMetaDTO::getReplicationFactor)
        .containsExactly(topicName, 6, CleanupPolicy.DELETE, (short) 2);
  }

  @Test
  void concurrentCreateRace_describeFailureDefersActualPublication() throws Exception {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
    @SuppressWarnings("unchecked")
    KafkaFuture<Void> failedFuture = mock(KafkaFuture.class);
    when(adminClient.createTopics(anyList())).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(failedFuture);
    when(failedFuture.get())
        .thenThrow(new ExecutionException(new TopicExistsException("already created elsewhere")));
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
    @SuppressWarnings("unchecked")
    KafkaFuture<TopicDescription> describeFuture = mock(KafkaFuture.class);
    when(adminClient.describeTopics(Set.of(topicName))).thenReturn(describeTopicsResult);
    when(describeTopicsResult.topicNameValues()).thenReturn(Map.of(topicName, describeFuture));
    when(describeFuture.get())
        .thenThrow(new ExecutionException(new IllegalStateException("describe failed")));

    dynamicTopicManager.processRequestedTopic(topicName, topicMeta);

    assertThat(cachedRequestTopicMetaMap()).containsEntry(topicName, topicMeta);
    assertThat(
            dynamicTopicManager.topicExists(
                Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker"))
        .isFalse();
    verify(topicMetaProducer, never()).send(anyProducerRecord(), any());
  }

  @Test
  void topicCreateFailure_doesNotPublishOrCacheAsActual() throws Exception {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
    @SuppressWarnings("unchecked")
    KafkaFuture<Void> failedFuture = mock(KafkaFuture.class);
    when(adminClient.createTopics(anyList())).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(failedFuture);
    when(failedFuture.get())
        .thenThrow(new ExecutionException(new IllegalStateException("broker unavailable")));
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    dynamicTopicManager.processRequestedTopic(topicName, topicMeta);

    assertThat(cachedRequestTopicMetaMap()).containsEntry(topicName, topicMeta);
    assertThat(
            dynamicTopicManager.topicExists(
                Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker"))
        .isFalse();
    verify(topicMetaProducer, never()).send(anyProducerRecord(), any());
  }

  @Test
  void invalidFixedTopicRequest_isRejectedBeforeAnySideEffects() throws Exception {
    String topicName = LOCAL_PREFIX + "process-instance";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    dynamicTopicManager.processRequestedTopic(topicName, topicMeta);

    assertThat(cachedRequestTopicMetaMap()).isEmpty();
    verify(adminClient, never()).createTopics(anyList());
    verify(topicMetaProducer, never()).send(anyProducerRecord(), any());
  }

  @Test
  void registerManagedTopic_seedsCachesWithoutPublishingRequestedTopic() throws Exception {
    String topicName = LOCAL_PREFIX + "process-instance";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 12, CleanupPolicy.DELETE, (short) 1);

    dynamicTopicManager.registerManagedTopic(topicMeta);

    assertThat(cachedRequestTopicMetaMap()).containsEntry(topicName, topicMeta);
    assertThat(dynamicTopicManager.topicExists("process-instance")).isTrue();
    verify(topicMetaProducer, never()).send(anyProducerRecord(), any());
  }

  @Test
  void handleRequestedTopicRecord_authorizedRequestIsCollected() {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);
    Map<String, TopicMetaDTO> collectedTopics = new ConcurrentHashMap<>();
    ConsumerRecord<String, TopicMetaDTO> topicRecord =
        new ConsumerRecord<>(ACTUAL_TOPIC, 0, 0L, topicName, topicMeta);
    topicRecord.headers().add(Constants.HEADER_ENGINE_SIGNATURE, "client-key-1.AABB".getBytes());
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    dynamicTopicManager.handleRequestedTopicRecord(collectedTopics, topicRecord);

    assertThat(collectedTopics).containsEntry(topicName, topicMeta);
  }

  @Test
  void handleRequestedTopicRecord_unauthorizedRequestIsRejected() {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);
    Map<String, TopicMetaDTO> collectedTopics = new ConcurrentHashMap<>();
    ConsumerRecord<String, TopicMetaDTO> topicRecord =
        new ConsumerRecord<>(ACTUAL_TOPIC, 0, 0L, topicName, topicMeta);
    topicRecord.headers().add(Constants.HEADER_ENGINE_SIGNATURE, "client-key-1.AABB".getBytes());
    when(engineAuthorizationService.authorizeTopicMetaRequest(any(), any()))
        .thenThrow(
            new AuthorizationTokenException(
                "Unknown Ed25519 keyId 'client-key-1' — signer not found in taktx-signing-keys KTable"));
    DlqEnvelope dlqEnvelope = new DlqEnvelope();
    ArgumentCaptor<TopicMetaDlqEntryDTO> dlqEntryCaptor =
        ArgumentCaptor.forClass(TopicMetaDlqEntryDTO.class);
    when(dlqPublisher.toEnvelope(dlqEntryCaptor.capture(), anyLong(), anyString()))
        .thenReturn(dlqEnvelope);
    when(dlqPublisher.recordKey(dlqEnvelope)).thenReturn("topic-meta-requested");

    dynamicTopicManager.handleRequestedTopicRecord(collectedTopics, topicRecord);

    assertThat(collectedTopics).isEmpty();
    verify(topicMetaProducer).send(anyProducerRecord(), any());
    verify(dlqProducer).send(anyDlqProducerRecord(), any());
    assertThat(dlqEntryCaptor.getValue().getTopicName()).isEqualTo(topicName);
    assertThat(new String(dlqEntryCaptor.getValue().getHeaders().get(DlqHeaders.REASON_HINT)))
        .isEqualTo(DlqReasonCode.SIGNATURE_KEY_UNKNOWN.name());
    assertThat(dlqEntryCaptor.getValue().getData()).isNotEmpty();
  }

  @Test
  void reasonCodeForAuthorizationFailure_mapsKnownVerificationMessages() {
    assertThat(
            DynamicTopicManager.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Missing required X-TaktX-Signature header — required role: CLIENT")))
        .isEqualTo(DlqReasonCode.SIGNATURE_MISSING);
    assertThat(
            DynamicTopicManager.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Unknown Ed25519 keyId 'client-key-1' — signer not found in taktx-signing-keys KTable")))
        .isEqualTo(DlqReasonCode.SIGNATURE_KEY_UNKNOWN);
    assertThat(
            DynamicTopicManager.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Revoked Ed25519 keyId 'client-key-1' — rejecting message")))
        .isEqualTo(DlqReasonCode.SIGNATURE_KEY_REVOKED);
    assertThat(
            DynamicTopicManager.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Signing keyId 'client-key-1' (role=CLIENT) is not trusted for required role CLIENT")))
        .isEqualTo(DlqReasonCode.AUTHORIZATION_FAILED);
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<String, TopicMetaDTO> anyProducerRecord() {
    return any(ProducerRecord.class);
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<String, DlqEnvelope> anyDlqProducerRecord() {
    return any(ProducerRecord.class);
  }

  private SigningKeyDTO activeKey(String keyId, KeyRole role) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64("dummy")
        .algorithm("Ed25519")
        .owner("worker")
        .role(role)
        .build();
  }

  private void mockDescribeTopics(String topicName, int partitionCount, short replicationFactor) {
    DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
    when(adminClient.describeTopics(Set.of(topicName))).thenReturn(describeTopicsResult);
    when(describeTopicsResult.topicNameValues())
        .thenReturn(
            Map.of(
                topicName,
                KafkaFuture.completedFuture(
                    topicDescription(topicName, partitionCount, replicationFactor))));
  }

  private TopicDescription topicDescription(
      String topicName, int partitionCount, short replicationFactor) {
    List<Node> replicas =
        java.util.stream.IntStream.range(0, replicationFactor)
            .mapToObj(index -> new Node(index, "broker-" + index, 9092 + index))
            .toList();
    List<TopicPartitionInfo> partitions =
        java.util.stream.IntStream.range(0, partitionCount)
            .mapToObj(
                index -> new TopicPartitionInfo(index, replicas.getFirst(), replicas, replicas))
            .toList();
    return new TopicDescription(topicName, false, partitions);
  }

  @SuppressWarnings("unchecked")
  private Map<String, TopicMetaDTO> cachedRequestTopicMetaMap() throws Exception {
    Field field = DynamicTopicManager.class.getDeclaredField("cachedRequestTopicMetaMap");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, TopicMetaDTO>) field.get(dynamicTopicManager);
  }

  private static void setPrivateField(Object target, String fieldName, Object value)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
