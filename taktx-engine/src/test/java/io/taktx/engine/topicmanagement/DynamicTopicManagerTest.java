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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.CleanupPolicy;
import io.taktx.dto.Constants;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.engine.license.LicenseManager;
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

  private AdminClient adminClient;
  private TaktConfiguration taktConfiguration;
  private LicenseManager licenseManager;
  private KafkaProducer<String, TopicMetaDTO> topicMetaProducer;
  private DynamicTopicManager dynamicTopicManager;

  @BeforeEach
  void setUp() throws Exception {
    adminClient = mock(AdminClient.class);
    taktConfiguration = mock(TaktConfiguration.class);
    KafkaClientsConfig kafkaClientsConfig = mock(KafkaClientsConfig.class);
    licenseManager = mock(LicenseManager.class);
    topicMetaProducer = mock(KafkaProducer.class);

    when(taktConfiguration.getPrefixed(anyString()))
        .thenAnswer(invocation -> LOCAL_PREFIX + invocation.getArgument(0, String.class));
    when(licenseManager.getPartitionBudget()).thenReturn(Integer.MAX_VALUE);

    RequestedTopicValidator requestedTopicValidator =
        new RequestedTopicValidator(taktConfiguration);
    dynamicTopicManager =
        new DynamicTopicManager(
            adminClient,
            taktConfiguration,
            kafkaClientsConfig,
            licenseManager,
            requestedTopicValidator);

    setPrivateField(dynamicTopicManager, "topicMetaProducer", topicMetaProducer);
    setPrivateField(dynamicTopicManager, "cachedActualTopicName", ACTUAL_TOPIC);
  }

  @Test
  void validExternalTaskTopicRequest_createsTopicAndCachesMetadata() throws Exception {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta = new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1);

    CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
    when(adminClient.createTopics(anyList())).thenReturn(createTopicsResult);
    when(createTopicsResult.all()).thenReturn(KafkaFuture.completedFuture(null));

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

  @SuppressWarnings("unchecked")
  private ProducerRecord<String, TopicMetaDTO> anyProducerRecord() {
    return any(ProducerRecord.class);
  }

  private void mockDescribeTopics(String topicName, int partitionCount, short replicationFactor)
      throws Exception {
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
            .mapToObj(index -> new TopicPartitionInfo(index, replicas.get(0), replicas, replicas))
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
