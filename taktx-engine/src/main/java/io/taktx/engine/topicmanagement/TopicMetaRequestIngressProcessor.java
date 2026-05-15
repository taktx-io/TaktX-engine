/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import io.taktx.dto.Constants;
import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.security.DedupStoreSupport;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Streams-owned ingress handling for {@code topic-meta-requested}: authorization, validation,
 * duplicate suppression, rejection routing, and handoff to {@link DynamicTopicManager}.
 */
@Slf4j
public class TopicMetaRequestIngressProcessor
    implements Processor<String, TopicMetaDTO, String, TopicMetaDlqEntryDTO> {

  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

  private final Clock clock;
  private final long retentionMs;
  private final String storeName;
  private final String requestedTopicName;
  private final EngineAuthorizationService engineAuthorizationService;
  private final RequestedTopicValidator requestedTopicValidator;
  private final DynamicTopicManager topicManager;

  private ProcessorContext<String, TopicMetaDlqEntryDTO> context;
  private KeyValueStore<String, Long> store;

  public TopicMetaRequestIngressProcessor(
      Clock clock,
      long retentionMs,
      String storeName,
      String requestedTopicName,
      EngineAuthorizationService engineAuthorizationService,
      RequestedTopicValidator requestedTopicValidator,
      DynamicTopicManager topicManager) {
    this.clock = clock;
    this.retentionMs = retentionMs;
    this.storeName = storeName;
    this.requestedTopicName = requestedTopicName;
    this.engineAuthorizationService = engineAuthorizationService;
    this.requestedTopicValidator = requestedTopicValidator;
    this.topicManager = topicManager;
  }

  @Override
  public void init(ProcessorContext<String, TopicMetaDlqEntryDTO> context) {
    this.context = context;
    this.store = context.getStateStore(storeName);
    context.schedule(CLEANUP_INTERVAL, PunctuationType.WALL_CLOCK_TIME, this::purgeExpiredEntries);
  }

  @Override
  public void process(Record<String, TopicMetaDTO> inputRecord) {
    if (inputRecord == null || inputRecord.value() == null) {
      return;
    }

    TopicMetaDTO topicMeta = inputRecord.value();
    String effectiveTopicName = effectiveTopicName(inputRecord.key(), topicMeta);

    try {
      var trustedSigner =
          engineAuthorizationService.authorizeTopicMetaRequest(inputRecord.headers(), topicMeta);
      log.info(
          "Accepted topic meta ingress key='{}' topicName='{}' signerKeyId='{}' signerRole='{}' outcome='accepted'",
          inputRecord.key(),
          topicMeta.getTopicName(),
          trustedSigner.getKeyId(),
          trustedSigner.effectiveRole());
    } catch (AuthorizationTokenException e) {
      log.warn(
          "Rejected topic meta ingress key='{}' topicName='{}' signerKeyId='{}' outcome='rejected' reason='{}'",
          inputRecord.key(),
          effectiveTopicName,
          extractSignerKeyId(inputRecord.headers()),
          e.getMessage());
      topicManager.publishRejectedRequestedTopic(effectiveTopicName);
      forwardDlq(inputRecord, topicMeta, e);
      return;
    }

    RequestedTopicValidationResult validation =
        requestedTopicValidator.validate(inputRecord.key(), topicMeta);
    if (!validation.valid()) {
      log.warn(
          "Rejected topic meta ingress key='{}' topicName='{}' outcome='rejected' reason='{}'",
          inputRecord.key(),
          validation.topicName(),
          validation.rejectionReason());
      topicManager.publishRejectedRequestedTopic(validation.topicName());
      return;
    }

    String dedupKey = dedupKey(inputRecord, validation.topicName(), topicMeta);
    long now = clock.millis();
    Long storedTs = store.get(dedupKey);
    if (storedTs != null && now - storedTs < retentionMs) {
      log.warn(
          "Rejected duplicate topic-meta-requested key='{}' topicName='{}' dedupKey='{}' retentionMs={}",
          inputRecord.key(),
          validation.topicName(),
          dedupKey,
          retentionMs);
      return;
    }

    store.put(dedupKey, now);
    topicManager.processRequestedTopic(validation.topicName(), topicMeta);
  }

  private void purgeExpiredEntries(long timestamp) {
    DedupStoreSupport.purgeExpiredEntries(store, timestamp, retentionMs);
  }

  private void forwardDlq(
      Record<String, TopicMetaDTO> inputRecord,
      TopicMetaDTO topicMeta,
      AuthorizationTokenException exception) {
    context.forward(
        new Record<>(
            inputRecord.key(),
            topicMetaDlqEntry(inputRecord, topicMeta, exception),
            inputRecord.timestamp(),
            inputRecord.headers()));
  }

  private TopicMetaDlqEntryDTO topicMetaDlqEntry(
      Record<String, TopicMetaDTO> inputRecord,
      TopicMetaDTO topicMeta,
      AuthorizationTokenException exception) {
    DlqReasonCode reasonCode = reasonCodeForAuthorizationFailure(exception);
    Map<String, byte[]> headers = headersToMap(inputRecord.headers());
    headers.put(DlqHeaders.REASON_HINT, reasonCode.name().getBytes(StandardCharsets.UTF_8));
    headers.put(
        DlqHeaders.REASON_TEXT,
        String.valueOf(exception.getMessage()).getBytes(StandardCharsets.UTF_8));
    headers.put(
        DlqHeaders.CAPTURE_STAGE,
        DlqCaptureStage.PROCESSOR.name().getBytes(StandardCharsets.UTF_8));

    return new TopicMetaDlqEntryDTO(
        inputRecord.key(),
        topicMeta,
        headers,
        TopologyProducer.TOPIC_META_SERDE.serializer().serialize(requestedTopicName, topicMeta));
  }

  static DlqReasonCode reasonCodeForAuthorizationFailure(AuthorizationTokenException exception) {
    String message =
        exception == null || exception.getMessage() == null ? "" : exception.getMessage();
    String normalized = message.toLowerCase();

    if (normalized.startsWith("missing required x-taktx-signature header")) {
      return DlqReasonCode.SIGNATURE_MISSING;
    }
    if (normalized.startsWith("unknown ed25519 keyid")) {
      return DlqReasonCode.SIGNATURE_KEY_UNKNOWN;
    }
    if (normalized.startsWith("revoked ed25519 keyid")) {
      return DlqReasonCode.SIGNATURE_KEY_REVOKED;
    }
    if (normalized.startsWith("signing keyid")) {
      return DlqReasonCode.AUTHORIZATION_FAILED;
    }
    return DlqReasonCode.AUTHORIZATION_FAILED;
  }

  private static Map<String, byte[]> headersToMap(Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray())
        .collect(
            java.util.stream.Collectors.toMap(
                org.apache.kafka.common.header.Header::key,
                org.apache.kafka.common.header.Header::value));
  }

  private static String effectiveTopicName(String recordKey, TopicMetaDTO topicMeta) {
    if (recordKey != null && !recordKey.isBlank()) {
      return recordKey;
    }
    if (topicMeta != null
        && topicMeta.getTopicName() != null
        && !topicMeta.getTopicName().isBlank()) {
      return topicMeta.getTopicName();
    }
    return null;
  }

  private static String dedupKey(
      Record<String, TopicMetaDTO> inputRecord, String topicName, TopicMetaDTO topicMeta) {
    String messageId = topicMeta.getMessageId();
    String identity =
        messageId != null && !messageId.isBlank()
            ? "messageId:" + messageId
            : "payloadHash:" + signedPayloadHash(inputRecord, topicMeta);
    return TopicMetaDTO.class.getSimpleName() + ":" + topicName + ":" + identity;
  }

  private static String signedPayloadHash(
      Record<String, TopicMetaDTO> inputRecord, TopicMetaDTO topicMeta) {
    byte[] payload =
        TopologyProducer.TOPIC_META_SERDE.serializer().serialize(inputRecord.key(), topicMeta);
    Header signatureHeader =
        inputRecord.headers() != null
            ? inputRecord.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE)
            : null;
    byte[] signatureBytes =
        signatureHeader != null && signatureHeader.value() != null
            ? signatureHeader.value()
            : new byte[0];

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(signatureBytes);
      digest.update((byte) 0x00);
      if (payload != null) {
        digest.update(payload);
      }
      return toHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte current : bytes) {
      hex.append(String.format("%02x", current));
    }
    return hex.toString();
  }

  private static String extractSignerKeyId(Headers headers) {
    if (headers == null) {
      return null;
    }
    Header header = headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE);
    if (header == null || header.value() == null) {
      return null;
    }
    String headerValue = new String(header.value(), StandardCharsets.UTF_8);
    int dotIndex = headerValue.indexOf('.');
    return dotIndex >= 0 ? headerValue.substring(0, dotIndex) : headerValue;
  }
}
