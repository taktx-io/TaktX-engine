/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventDlqEntryDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageEventSignalDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.StartCommandDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class MessageEventProcessor
    implements Processor<MessageEventKeyDTO, MessageEventIngressEnvelope, Object, Object> {

  private static final String DLQ_REASON_HINT_HEADER = DlqHeaders.REASON_HINT;
  private static final String DLQ_REASON_TEXT_HEADER = DlqHeaders.REASON_TEXT;
  private static final String DLQ_CAPTURE_STAGE_HEADER = DlqHeaders.CAPTURE_STAGE;

  private final TaktConfiguration taktConfiguration;

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<MessageEventKeyDTO, DefinitionMessageSubscriptions>
      definitionMessageSubscriptionStore;
  private KeyValueStore<MessageEventKeyDTO, CorrelationMessageSubscriptions>
      correlationMessageSubscriptionStore;
  private final Clock clock;
  private final ProcessingStatistics processingStatistics;
  private final ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard;
  private final EngineAuthorizationService engineAuthorizationService;

  public MessageEventProcessor(
      TaktConfiguration taktConfiguration, Clock clock, ProcessingStatistics processingStatistics) {
    this(taktConfiguration, clock, processingStatistics, null, null);
  }

  public MessageEventProcessor(
      TaktConfiguration taktConfiguration,
      Clock clock,
      ProcessingStatistics processingStatistics,
      ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard) {
    this(
        taktConfiguration, clock, processingStatistics, protectedDataPlaneParticipationGuard, null);
  }

  public MessageEventProcessor(
      TaktConfiguration taktConfiguration,
      Clock clock,
      ProcessingStatistics processingStatistics,
      ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard,
      EngineAuthorizationService engineAuthorizationService) {
    this.taktConfiguration = taktConfiguration;
    this.clock = clock;
    this.processingStatistics = processingStatistics;
    this.protectedDataPlaneParticipationGuard = protectedDataPlaneParticipationGuard;
    this.engineAuthorizationService = engineAuthorizationService;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionMessageSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()));
    this.correlationMessageSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()));
  }

  @Override
  public void process(Record<MessageEventKeyDTO, MessageEventIngressEnvelope> messageEventRecord) {
    MessageEventIngressEnvelope ingressEnvelope = messageEventRecord.value();
    MessageEventDTO value = ingressEnvelope != null ? ingressEnvelope.value() : null;
    if (value == null) {
      emitMessageEventDlq(
          messageEventRecord,
          "PAYLOAD_DESERIALIZATION_ERROR",
          "Null payload for message-event record",
          "DESERIALIZER");
      return;
    }
    // Record end-to-end latency using Kafka timestamp
    processingStatistics.recordMessageEventLatency(
        messageEventRecord.timestamp(), value.getClass().getSimpleName());

    try {
      switch (value) {
        case DefinitionMessageSubscriptionDTO startEventMessageSubscription ->
            storeDefinitionMessageSubscription(
                messageEventRecord.key(), startEventMessageSubscription);
        case CorrelationMessageSubscriptionDTO correlatingMessageSubscription ->
            storeCorrelationMessageSubscription(
                messageEventRecord.key(), correlatingMessageSubscription);
        case CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscription ->
            cancelDefinitionMessageSubscription(
                messageEventRecord.key(), cancelDefinitionMessageSubscription);
        case CancelCorrelationMessageSubscriptionDTO cancelCorrelatingMessageSubscription ->
            cancelCorrelationMessageSubscription(
                messageEventRecord.key(), cancelCorrelatingMessageSubscription);
        case DefinitionMessageEventTriggerDTO messageEvent -> {
          if (shouldRejectUnauthorizedExternalIngress(messageEventRecord, ingressEnvelope)) {
            return;
          }
          if (shouldBlockProtectedDataPlane(messageEventRecord)) {
            return;
          }
          processDefinitionMessageEventTrigger(messageEventRecord.key(), messageEvent);
        }
        case CorrelationMessageEventTriggerDTO messageEvent -> {
          if (shouldRejectUnauthorizedExternalIngress(messageEventRecord, ingressEnvelope)) {
            return;
          }
          if (shouldBlockProtectedDataPlane(messageEventRecord)) {
            return;
          }
          processCorrelationMessageEventTrigger(messageEventRecord.key(), messageEvent);
        }
        default -> {
          log.warn("⚠ Unknown message-event type, routing to DLQ: {}", value.getClass().getName());
          emitMessageEventDlq(
              messageEventRecord,
              "PAYLOAD_TYPE_MISMATCH",
              "Unknown message event type: " + value.getClass().getName(),
              "PROCESSOR");
        }
      }
    } catch (Exception e) {
      log.error(
          "⚠ Exception processing message-event record, routing to DLQ: {}", e.getMessage(), e);
      emitMessageEventDlq(messageEventRecord, "PROCESSOR_EXCEPTION", e.getMessage(), "PROCESSOR");
    }
  }

  private boolean shouldBlockProtectedDataPlane(
      Record<MessageEventKeyDTO, MessageEventIngressEnvelope> messageEventRecord) {
    if (protectedDataPlaneParticipationGuard == null) {
      return false;
    }
    ProtectedDataPlaneParticipationGuard.Decision decision =
        protectedDataPlaneParticipationGuard.evaluate();
    if (decision.permitted()) {
      return false;
    }
    emitMessageEventDlq(
        messageEventRecord, decision.reasonHint(), decision.reasonText(), "PROCESSOR");
    return true;
  }

  private void emitMessageEventDlq(
      Record<MessageEventKeyDTO, MessageEventIngressEnvelope> messageEventRecord,
      String reasonHint,
      String reasonText,
      String captureStage) {
    Map<String, byte[]> headersMap = headersToMap(messageEventRecord.headers());
    headersMap.put(DLQ_REASON_HINT_HEADER, reasonHint.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_REASON_TEXT_HEADER, reasonText.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_CAPTURE_STAGE_HEADER, captureStage.getBytes(StandardCharsets.UTF_8));
    MessageEventDlqEntryDTO dlqEntry =
        new MessageEventDlqEntryDTO(
            messageEventRecord.key(),
            messageEventRecord.value() == null ? null : messageEventRecord.value().value(),
            headersMap);
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private boolean shouldRejectUnauthorizedExternalIngress(
      Record<MessageEventKeyDTO, MessageEventIngressEnvelope> messageEventRecord,
      MessageEventIngressEnvelope ingressEnvelope) {
    if (engineAuthorizationService == null
        || !EngineAuthorizationService.isExternallyPublishedMessageEvent(
            ingressEnvelope == null ? null : ingressEnvelope.value())) {
      return false;
    }
    try {
      engineAuthorizationService.authorizeMessageEventIngress(
          messageEventRecord.headers(), ingressEnvelope);
      return false;
    } catch (AuthorizationTokenException e) {
      emitMessageEventDlq(
          messageEventRecord,
          reasonHintForAuthorizationFailure(ingressEnvelope, e),
          e.getMessage(),
          "AUTHORIZATION");
      return true;
    }
  }

  private static String reasonHintForAuthorizationFailure(
      MessageEventIngressEnvelope ingressEnvelope, AuthorizationTokenException exception) {
    String signatureError = ingressEnvelope != null ? ingressEnvelope.signatureError() : null;
    if (signatureError != null && !signatureError.isBlank()) {
      String normalized = signatureError.toLowerCase();
      if (normalized.contains("unknown or revoked")) {
        return DlqReasonCode.SIGNATURE_KEY_UNKNOWN.name();
      }
      if (normalized.contains("revoked")) {
        return DlqReasonCode.SIGNATURE_KEY_REVOKED.name();
      }
      if (normalized.contains("unknown")) {
        return DlqReasonCode.SIGNATURE_KEY_UNKNOWN.name();
      }
      if (normalized.contains("malformed")) {
        return DlqReasonCode.SIGNATURE_MALFORMED.name();
      }
      return DlqReasonCode.SIGNATURE_VERIFICATION_FAILED.name();
    }
    String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
    if (message.contains("requires tx-sig")
        || message.startsWith("missing required tx-sig header")) {
      return DlqReasonCode.SIGNATURE_MISSING.name();
    }
    if (message.startsWith("unknown ed25519 keyid")) {
      return DlqReasonCode.SIGNATURE_KEY_UNKNOWN.name();
    }
    if (message.startsWith("revoked ed25519 keyid")) {
      return DlqReasonCode.SIGNATURE_KEY_REVOKED.name();
    }
    if (message.contains("platform public key") || message.contains("anchored trust")) {
      return "TRUST_ANCHOR_MISSING";
    }
    return DlqReasonCode.AUTHORIZATION_FAILED.name();
  }

  private static Map<String, byte[]> headersToMap(org.apache.kafka.common.header.Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray()).collect(Collectors.toMap(Header::key, Header::value));
  }

  private void cancelDefinitionMessageSubscription(
      MessageEventKeyDTO key,
      CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(key);
    if (messageSubscriptions != null) {
      DefinitionMessageSubscriptions removed =
          messageSubscriptions.remove(cancelDefinitionMessageSubscription);
      if (removed.getDefinitions().isEmpty()) {
        this.definitionMessageSubscriptionStore.put(key, null);
      } else {
        this.definitionMessageSubscriptionStore.put(key, removed);
      }
    }
  }

  private void cancelCorrelationMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      CancelCorrelationMessageSubscriptionDTO cancelCorrelatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      CorrelationMessageSubscriptions removed =
          messageSubscriptions.remove(cancelCorrelatingMessageSubscription.getCorrelationKey());
      if (removed.getInstances().isEmpty()) {
        this.correlationMessageSubscriptionStore.put(messageEventKey, null);
      } else {
        this.correlationMessageSubscriptionStore.put(messageEventKey, removed);
      }
    }
  }

  private void processCorrelationMessageEventTrigger(
      MessageEventKeyDTO messageEventKey, CorrelationMessageEventTriggerDTO messageEvent) {

    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      messageSubscriptions
          .getInstances()
          .values()
          .forEach(
              subscription -> {
                if (subscription.getCorrelationKey().equals(messageEvent.getCorrelationKey())) {
                  UUID processInstanceId = subscription.getProcessInstanceId();
                  MessageEventSignalDTO messageEventSignalDTO = new MessageEventSignalDTO();
                  messageEventSignalDTO.setName(messageEvent.getMessageName());
                  messageEventSignalDTO.setElementInstanceIdPath(
                      subscription.getElementInstanceIdPath());
                  messageEventSignalDTO.setElementId(subscription.getElementId());
                  messageEventSignalDTO.setVariables(messageEvent.getVariables());
                  EventSignalTriggerDTO eventSignalTrigger =
                      new EventSignalTriggerDTO(processInstanceId, messageEventSignalDTO);
                  context.forward(
                      new Record<>(processInstanceId, eventSignalTrigger, clock.millis()));
                }
              });
    }
  }

  private void processDefinitionMessageEventTrigger(
      MessageEventKeyDTO messageEventKey, DefinitionMessageEventTriggerDTO messageEvent) {
    DefinitionMessageSubscriptions messageSubscription =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscription != null) {
      messageSubscription
          .getDefinitions()
          .values()
          .forEach(
              value -> {
                if (value.getMessageName().equals(messageEvent.getMessageName())) {
                  ProcessDefinitionKey processDefinitionKey = value.getProcessDefinitionKey();
                  UUID processInstanceId = UUID.randomUUID();
                  StartCommandDTO startCommand =
                      new StartCommandDTO(
                          processInstanceId,
                          value.getElementId(),
                          List.of(),
                          new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId()),
                          messageEvent.getVariables());

                  context.forward(new Record<>(processInstanceId, startCommand, clock.millis()));
                }
              });
    }
  }

  private void storeCorrelationMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      CorrelationMessageSubscriptionDTO correlatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new CorrelationMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(correlatingMessageSubscription);
    this.correlationMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }

  private void storeDefinitionMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      DefinitionMessageSubscriptionDTO startEventMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new DefinitionMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(startEventMessageSubscription);
    this.definitionMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }
}
