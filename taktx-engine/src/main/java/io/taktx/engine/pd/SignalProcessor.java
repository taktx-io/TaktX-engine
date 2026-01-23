/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.SignalEventSignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.SignalDefinitionSubscriptionKeyDTO;
import io.taktx.engine.generic.SignalInstanceSubscriptionKeyDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class SignalProcessor
    implements Processor<String, SignalDTO, UUID, ProcessInstanceTriggerDTO> {

  private final TaktConfiguration taktConfiguration;
  private final Clock clock;
  private KeyValueStore<SignalInstanceSubscriptionKeyDTO, String> instanceSignalSubscriptionStore;
  private KeyValueStore<SignalDefinitionSubscriptionKeyDTO, String>
      definitionSignalSubscriptionStore;
  private ProcessorContext<UUID, ProcessInstanceTriggerDTO> context;

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
  public void init(ProcessorContext<UUID, ProcessInstanceTriggerDTO> context) {
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
    return null;
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
