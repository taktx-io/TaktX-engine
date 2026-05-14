/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import static io.taktx.dto.Constants.MAX_LONG;

import io.taktx.dto.CancelDefinitionSignalSubscriptionDTO;
import io.taktx.dto.CancelInstanceSignalSubscriptionDTO;
import io.taktx.dto.Constants;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.NewDefinitionSignalSubscriptionDTO;
import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.SignalDlqEntryDTO;
import io.taktx.dto.SignalEventSignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.generic.SignalDefinitionSubscriptionKeyDTO;
import io.taktx.engine.generic.SignalInstanceSubscriptionKeyDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class SignalProcessor implements Processor<String, SignalDTO, Object, Object> {

  private static final String DLQ_REASON_HINT_HEADER = DlqHeaders.REASON_HINT;
  private static final String DLQ_REASON_TEXT_HEADER = DlqHeaders.REASON_TEXT;
  private static final String DLQ_CAPTURE_STAGE_HEADER = DlqHeaders.CAPTURE_STAGE;

  private final TaktConfiguration taktConfiguration;
  private final Clock clock;
  private KeyValueStore<SignalInstanceSubscriptionKeyDTO, String> instanceSignalSubscriptionStore;
  private KeyValueStore<SignalDefinitionSubscriptionKeyDTO, String>
      definitionSignalSubscriptionStore;
  private ProcessorContext<Object, Object> context;

  private static final ThreadLocal<MessageDigest> SHA256_DIGEST =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
              throw new IllegalStateException(e);
            }
          });

  public SignalProcessor(TaktConfiguration taktConfiguration, Clock clock) {
    this.taktConfiguration = taktConfiguration;
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.instanceSignalSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename()));
    this.definitionSignalSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename()));
    this.context = context;
  }

  @Override
  public void process(Record<String, SignalDTO> singalRecord) {
    if (singalRecord.value() == null) {
      emitSignalDlq(
          singalRecord, "CBOR_DECODE_ERROR", "Null payload for signals record", "DESERIALIZER");
      return;
    }
    try {
      if (singalRecord.value()
          instanceof NewInstanceSignalSubscriptionDTO newInstanceSignalSubscriptionDTO) {
        SignalInstanceSubscriptionKeyDTO key =
            new SignalInstanceSubscriptionKeyDTO(
                hash(newInstanceSignalSubscriptionDTO.getSignalName()),
                newInstanceSignalSubscriptionDTO.getProcessInstanceId(),
                newInstanceSignalSubscriptionDTO.getElementInstanceIdPath());
        instanceSignalSubscriptionStore.put(key, newInstanceSignalSubscriptionDTO.getSignalName());
      } else if (singalRecord.value()
          instanceof CancelInstanceSignalSubscriptionDTO cancelInstanceSignalSubscriptionDTO) {
        SignalInstanceSubscriptionKeyDTO key =
            new SignalInstanceSubscriptionKeyDTO(
                hash(cancelInstanceSignalSubscriptionDTO.getSignalName()),
                cancelInstanceSignalSubscriptionDTO.getProcessInstanceId(),
                cancelInstanceSignalSubscriptionDTO.getElementInstanceIdPath());
        instanceSignalSubscriptionStore.delete(key);
      } else if (singalRecord.value()
          instanceof NewDefinitionSignalSubscriptionDTO newDefinitionSignalSubscriptionDTO) {
        SignalDefinitionSubscriptionKeyDTO key =
            new SignalDefinitionSubscriptionKeyDTO(
                hash(newDefinitionSignalSubscriptionDTO.getSignalName()),
                newDefinitionSignalSubscriptionDTO.getProcessDefinitionKey(),
                newDefinitionSignalSubscriptionDTO.getElementId());
        definitionSignalSubscriptionStore.put(
            key, newDefinitionSignalSubscriptionDTO.getSignalName());
      } else if (singalRecord.value()
          instanceof CancelDefinitionSignalSubscriptionDTO cancelDefinitionSignalSubscriptionDTO) {
        SignalDefinitionSubscriptionKeyDTO key =
            new SignalDefinitionSubscriptionKeyDTO(
                hash(cancelDefinitionSignalSubscriptionDTO.getSignalName()),
                cancelDefinitionSignalSubscriptionDTO.getProcessDefinitionKey(),
                cancelDefinitionSignalSubscriptionDTO.getElementId());
        definitionSignalSubscriptionStore.delete(key);
      }
      // Handle this one last as all others are subclasses
      else if (singalRecord.value() instanceof SignalDTO signalDTO) {
        handleTriggerSignal(signalDTO);
      }
    } catch (Exception e) {
      log.error("⚠ Exception processing signals record, routing to DLQ: {}", e.getMessage(), e);
      emitSignalDlq(singalRecord, "PROCESSOR_EXCEPTION", e.getMessage(), "PROCESSOR");
    }
  }

  private void emitSignalDlq(
      Record<String, SignalDTO> signalRecord,
      String reasonHint,
      String reasonText,
      String captureStage) {
    Map<String, byte[]> headersMap = headersToMap(signalRecord.headers());
    headersMap.put(DLQ_REASON_HINT_HEADER, reasonHint.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_REASON_TEXT_HEADER, reasonText.getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_CAPTURE_STAGE_HEADER, captureStage.getBytes(StandardCharsets.UTF_8));
    SignalDlqEntryDTO dlqEntry =
        new SignalDlqEntryDTO(signalRecord.key(), signalRecord.value(), headersMap);
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private static Map<String, byte[]> headersToMap(org.apache.kafka.common.header.Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray()).collect(Collectors.toMap(Header::key, Header::value));
  }

  private void handleTriggerSignal(SignalDTO signalDTO) {
    handleDefinitionSignals(signalDTO);
    handleInstanceSignals(signalDTO);
  }

  // Returns the smallest byte[] strictly greater than any array that starts with 'prefix'.
  // If all bytes are 0xFF, returns null to indicate there is no upper bound.
  public static byte[] prefixExclusiveUpperBound(byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      int val = prefix[i] & 0xFF;
      if (val != 0xFF) {
        byte[] upper = Arrays.copyOf(prefix, i + 1);
        upper[i] = (byte) (val + 1);
        return upper;
      }
    }
    return new byte[0];
  }

  private void handleDefinitionSignals(SignalDTO signalDTO) {
    byte[] startHash = hash(signalDTO.getSignalName());
    byte[] endHash = prefixExclusiveUpperBound(startHash);
    SignalDefinitionSubscriptionKeyDTO start =
        new SignalDefinitionSubscriptionKeyDTO(startHash, new ProcessDefinitionKey("", 0), null);
    SignalDefinitionSubscriptionKeyDTO end =
        new SignalDefinitionSubscriptionKeyDTO(endHash, new ProcessDefinitionKey("", 0), null);

    try (KeyValueIterator<SignalDefinitionSubscriptionKeyDTO, String> range =
        definitionSignalSubscriptionStore.range(start, end)) {
      range.forEachRemaining(
          subscription -> {
            UUID processInstanceId = UUID.randomUUID();
            StartCommandDTO startCommand =
                new StartCommandDTO(
                    processInstanceId,
                    subscription.key.getElementId(),
                    null,
                    subscription.key.getProcessDefinitionKey(),
                    VariablesDTO.empty());
            context.forward(new Record<>(processInstanceId, startCommand, clock.millis()));
          });
    }
  }

  private void handleInstanceSignals(SignalDTO signalDTO) {
    byte[] hash = hash(signalDTO.getSignalName());

    SignalInstanceSubscriptionKeyDTO start =
        new SignalInstanceSubscriptionKeyDTO(hash, Constants.MIN_UUID, List.of());
    SignalInstanceSubscriptionKeyDTO end =
        new SignalInstanceSubscriptionKeyDTO(hash, Constants.MAX_UUID, List.of(MAX_LONG));

    try (KeyValueIterator<SignalInstanceSubscriptionKeyDTO, String> range =
        instanceSignalSubscriptionStore.range(start, end)) {
      range.forEachRemaining(
          subscription -> {
            UUID processInstanceId = subscription.key.getProcessInstanceId();
            List<Long> elementInstanceIdPath = subscription.key.getElementInstanceIdPath();
            SignalEventSignalDTO event = new SignalEventSignalDTO();
            event.setName(signalDTO.getSignalName());
            event.setElementInstanceIdPath(elementInstanceIdPath);
            event.setVariables(VariablesDTO.empty());
            EventSignalTriggerDTO eventSignalTrigger =
                new EventSignalTriggerDTO(processInstanceId, event);
            context.forward(new Record<>(processInstanceId, eventSignalTrigger, clock.millis()));
          });
    }
  }

  private byte[] hash(String input) {
    byte[] digest = SHA256_DIGEST.get().digest(input.getBytes(StandardCharsets.UTF_8));

    byte[] truncated = new byte[16];
    System.arraycopy(digest, 0, truncated, 0, 16);

    return truncated;
  }
}
