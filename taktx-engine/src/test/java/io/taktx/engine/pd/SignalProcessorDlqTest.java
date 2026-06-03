/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static io.taktx.engine.dlq.DlqHeaders.CAPTURE_STAGE;
import static io.taktx.engine.dlq.DlqHeaders.REASON_HINT;
import static io.taktx.engine.dlq.DlqHeaders.REASON_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.NewDefinitionSignalSubscriptionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.SignalDlqEntryDTO;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.SignalDefinitionSubscriptionKeyDTO;
import io.taktx.engine.generic.SignalInstanceSubscriptionKeyDTO;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.EngineSecurityReadinessEvaluator;
import io.taktx.engine.security.MessageSigningService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.NamespaceSecurityPolicySupport;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class SignalProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private TaktConfiguration taktConfiguration;
  private Clock clock;
  private SignalProcessor processor;
  private KeyValueStore<SignalInstanceSubscriptionKeyDTO, String> instanceStore;
  private KeyValueStore<SignalDefinitionSubscriptionKeyDTO, String> definitionStore;
  private EngineAuthorizationService engineAuthorizationService;

  @BeforeEach
  void setUp() {
    taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getTenantId()).thenReturn("tenant");
    when(taktConfiguration.getNamespace()).thenReturn("bank.payments");
    when(taktConfiguration.getHost()).thenReturn("engine-host");
    when(taktConfiguration.getPort()).thenReturn(8080);
    when(taktConfiguration.getSigningIdentitySourceType()).thenReturn("file");
    when(taktConfiguration.getEngineKeyRegistrationSignature())
        .thenReturn("engine-registration-signature");
    when(taktConfiguration.getPlatformPublicKey()).thenReturn(null);
    when(taktConfiguration.getPrefixed(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename());
    when(taktConfiguration.getPrefixed(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename());

    clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    engineAuthorizationService = mock(EngineAuthorizationService.class);
    processor = new SignalProcessor(taktConfiguration, clock);

    context = mock(ProcessorContext.class);
    instanceStore = mock(KeyValueStore.class);
    definitionStore = mock(KeyValueStore.class);

    when(context.getStateStore(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(instanceStore);
    when(context.getStateStore(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(definitionStore);

    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Origin", "client".getBytes(StandardCharsets.UTF_8));
    Record<String, SignalIngressEnvelope> signalRecord =
        new Record<>(
            "signal-key", new SignalIngressEnvelope(null, null, false, null, null), 100L, headers);

    processor.process(signalRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(SignalDlqEntryDTO.class);

    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getSignalKey()).isEqualTo("signal-key");
    assertThat(dlqEntry.getValue()).isNull();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PAYLOAD_DESERIALIZATION_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_storeThrowsException_emitsDlqWithProcessorExceptionHint() {
    // Make definitionStore.range() return an empty iterator so handleDefinitionSignals succeeds.
    // Then instanceStore.range() throws, triggering the DLQ path.
    @SuppressWarnings("unchecked")
    KeyValueIterator<SignalDefinitionSubscriptionKeyDTO, String> emptyDefIt =
        mock(KeyValueIterator.class);
    when(emptyDefIt.hasNext()).thenReturn(false);
    when(definitionStore.range(any(), any())).thenReturn(emptyDefIt);
    when(instanceStore.range(any(), any())).thenThrow(new RuntimeException("store unavailable"));

    SignalDTO signal = new SignalDTO("order-placed");
    RecordHeaders headers = new RecordHeaders();
    Record<String, SignalIngressEnvelope> signalRecord =
        new Record<>("order-placed", envelope(signal), 200L, headers);

    processor.process(signalRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.value()).isInstanceOf(SignalDlqEntryDTO.class);

    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getSignalKey()).isEqualTo("order-placed");
    assertThat(dlqEntry.getValue()).isSameAs(signal);
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR_EXCEPTION");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("store unavailable");
  }

  @Test
  void process_signalTriggerUnderAnchoredPolicyWithoutTrustAnchor_emitsDlq() {
    SignalProcessor guardedProcessor = guardedProcessorWithPolicy(anchoredPolicy(42L));
    SignalDTO signal = new SignalDTO("order-placed");

    guardedProcessor.process(
        new Record<>("order-placed", envelope(signal), 300L, new RecordHeaders()));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) captor.getValue().value();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("TRUST_ANCHOR_MISSING");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("platform public key");
  }

  @Test
  void process_subscriptionMutationUnderAnchoredPolicy_remainsAllowed() {
    SignalProcessor guardedProcessor = guardedProcessorWithPolicy(anchoredPolicy(42L));
    NewDefinitionSignalSubscriptionDTO subscription =
        new NewDefinitionSignalSubscriptionDTO(
            new ProcessDefinitionKey("proc", 1), "start", "order-placed");

    guardedProcessor.process(
        new Record<>("order-placed", envelope(subscription), 300L, new RecordHeaders()));

    verify(definitionStore).put(any(), eq("order-placed"));
    verify(context, never()).forward(any());
  }

  @Test
  void process_signalAuthorizationFailure_emitsDlqWithSignatureHint() {
    SignalProcessor guardedProcessor =
        new SignalProcessor(taktConfiguration, clock, null, engineAuthorizationService);
    guardedProcessor.init(context);
    SignalDTO signal = new SignalDTO("order-placed");
    SignalIngressEnvelope ingressEnvelope = envelope(signal);
    RecordHeaders headers = new RecordHeaders();
    when(engineAuthorizationService.authorizeSignalIngress(headers, ingressEnvelope))
        .thenThrow(
            new AuthorizationTokenException(
                "Missing required tx-sig header — required role: CLIENT"));

    guardedProcessor.process(new Record<>("order-placed", ingressEnvelope, 320L, headers));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) captor.getValue().value();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("SIGNATURE_MISSING");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("AUTHORIZATION");
  }

  private SignalProcessor guardedProcessorWithPolicy(
      NamespaceSecurityPolicyDTO authoritativePolicy) {
    NamespaceSecurityPolicyStore policyStore = new NamespaceSecurityPolicyStore();
    policyStore.update(authoritativePolicy);
    MessageSigningService messageSigningService = mock(MessageSigningService.class);
    when(messageSigningService.getKeyId()).thenReturn("engine-key-1");
    when(messageSigningService.isPublicKeyPublished()).thenReturn(true);

    SignalProcessor guardedProcessor =
        new SignalProcessor(
            taktConfiguration,
            clock,
            new ProtectedDataPlaneParticipationGuard(
                policyStore,
                new EngineSecurityReadinessEvaluator(
                    taktConfiguration, policyStore, messageSigningService, clock),
                clock),
            null);
    guardedProcessor.init(context);
    return guardedProcessor;
  }

  private static SignalIngressEnvelope envelope(SignalDTO value) {
    return new SignalIngressEnvelope(new byte[0], value, false, null, null);
  }

  private static NamespaceSecurityPolicyDTO anchoredPolicy(long version) {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder()
            .mode(SecurityMode.ANCHORED)
            .policyVersion(version)
            .build());
  }
}
