/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.OpenKeyTrustPolicy;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive matrix test for {@link EngineAuthorizationService#authorize}.
 *
 * <p>Two independent security gates are validated across all combinations:
 *
 * <ul>
 *   <li><b>Auth gate</b>: {@code config.engineRequiresAuthorization} (C-ERA) × {@code
 *       license.isEngineRequiresAuthorization} (L-ERA)
 *   <li><b>Signing gate</b>: {@code config.signingEnabled} (C-SE) × {@code
 *       license.isEventSigningAllowed} (L-ESA)
 * </ul>
 *
 * <pre>
 * Matrix key:
 *  C-ERA    = config.engineRequiresAuthorization
 *  C-SE     = config.signingEnabled
 *  non-entry = ContinueFlowElementTriggerDTO (engine-internal continuation)
 *  entry     = StartCommandDTO | AbortTriggerDTO | SetVariableTriggerDTO (external commands)
 *
 * ┌────┬───────┬──────┬────────────────┬───────────────┬──────────┬──────────────────────────────────────────────┐
 * │ #  │ C-ERA │ C-SE │ Cmd type       │ Sig hdr(role) │ JWT hdr  │ Expected                                     │
 * ├────┼───────┼──────┼────────────────┼───────────────┼──────────┼──────────────────────────────────────────────┤
 * │  1 │ false │false │ non-entry      │ absent        │ absent   │ null                                         │
 * │  2 │ false │false │ non-entry      │ CLIENT        │ absent   │ null (sig ignored)                           │
 * │  3 │ false │false │ entry (start)  │ absent        │ absent   │ null (no auth)                               │
 * │  6 │ false │true  │ non-entry      │ absent        │ absent   │ throw (missing sig)                          │
 * │  7 │ false │true  │ non-entry      │ CLIENT        │ absent   │ verify Ed25519 / SIGNATURE_VERIFIED          │
 * │  8a│ false │true  │ entry (start)  │ absent        │ absent   │ throw (signing requires sig on entry too)    │
 * │  8b│ false │true  │ entry (start)  │ ENGINE        │ absent   │ verify / ENGINE_SIGNED                       │
 * │  8c│ false │true  │ entry (start)  │ CLIENT        │ absent   │ verify / SIGNATURE_VERIFIED (auth gate off)  │
 * │ 10 │ true  │false │ non-entry      │ absent        │ absent   │ current trust metadata                       │
 * │ 11 │ true  │false │ non-entry      │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 12 │ true  │true  │ non-entry      │ absent        │ absent   │ throw (missing sig)                          │
 * │ 13 │ true  │true  │ non-entry      │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 14 │ true  │true  │ non-entry      │ absent        │ absent   │ throw (signing enforced)                     │
 * │ 15 │ true  │true  │ non-entry      │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 18 │ true  │false │ entry (start)  │ absent        │ absent   │ throw (missing JWT/sig)                      │
 * │ 19 │ true  │false │ entry (start)  │ ENGINE        │ absent   │ ENGINE_SIGNED (ENGINE satisfies auth gate)   │
 * │ 20 │ true  │false │ entry (start)  │ CLIENT        │ absent   │ throw (CLIENT ≠ ENGINE, JWT missing)         │
 * │ 21 │ true  │true  │ entry (start)  │ absent        │ present  │ throw (signing gate: missing Ed25519)        │
 * │ 22 │ true  │true  │ entry (start)  │ CLIENT        │ present  │ JWT_AND_ED25519 (both gates pass)            │
 * │ 23 │ true  │true  │ entry (start)  │ ENGINE        │ absent   │ ENGINE_SIGNED (satisfies both gates)         │
 * │ 24 │ true  │true  │ entry (start)  │ CLIENT        │ absent   │ throw (CLIENT satisfies signing, not auth)   │
 * │ S1 │ false │true  │ entry (setVar) │ absent        │ absent   │ throw (signing gate: entry requires sig)     │
 * │ S2 │ true  │false │ entry (setVar) │ absent        │ absent   │ throw (auth gate: missing JWT/ENGINE-sig)    │
 * │ S3 │ true  │false │ entry (setVar) │ ENGINE        │ absent   │ ENGINE_SIGNED                                │
 * │ S4 │ true  │true  │ entry (setVar) │ CLIENT        │ present  │ JWT_AND_ED25519 (SET_VARIABLE action)        │
 * └────┴───────┴──────┴────────────────┴───────────────┴──────────┴──────────────────────────────────────────────┘
 * </pre>
 */
@SuppressWarnings("unchecked")
class EngineAuthorizationServiceMatrixTest {

  private static final String WORKER_KEY_ID = "worker-key-001";
  private static final String ENGINE_KEY_ID = "engine-key-001";
  private static final String PLATFORM_KID = "platform-key-matrix";
  private static final String ISSUER = "taktx-platform";

  private TaktConfiguration taktConfig;
  private GlobalConfigStore globalConfigStore;
  private PublicKeyProvider publicKeyProvider;
  private NonceStore nonceStore;
  private KafkaStreams kafkaStreams;
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;
  private KeyPair rsaKeyPair;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

    taktConfig = mock(TaktConfiguration.class);
    globalConfigStore = new GlobalConfigStore();
    publicKeyProvider = mock(PublicKeyProvider.class);
    nonceStore = new NonceStore();
    kafkaStreams = mock(KafkaStreams.class);
    signingKeysStore = mock(ReadOnlyKeyValueStore.class);

    when(publicKeyProvider.getKey(PLATFORM_KID)).thenReturn(rsaKeyPair.getPublic());
    when(kafkaStreams.store(org.mockito.ArgumentMatchers.any())).thenReturn(signingKeysStore);
    when(taktConfig.getPrefixed(org.mockito.ArgumentMatchers.any()))
        .thenReturn("default.taktx-signing-keys");

    // Register a WORKER-role and an ENGINE-role key in the KTable mock
    when(signingKeysStore.get(WORKER_KEY_ID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(WORKER_KEY_ID)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .owner("billing-worker")
                .role(KeyRole.CLIENT)
                .build());

    when(signingKeysStore.get(ENGINE_KEY_ID))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(ENGINE_KEY_ID)
                .publicKeyBase64("dummy")
                .status(KeyStatus.ACTIVE)
                .owner("engine")
                .role(KeyRole.ENGINE)
                .build());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Builds a service with explicit config flags. */
  private EngineAuthorizationService service(boolean cEra, boolean cSe) {
    globalConfigStore.update(
        GlobalConfigurationDTO.builder()
            .engineRequiresAuthorization(cEra)
            .signingEnabled(cSe)
            .build());
    return new EngineAuthorizationService(
        taktConfig,
        globalConfigStore,
        publicKeyProvider,
        nonceStore,
        kafkaStreams,
        new OpenKeyTrustPolicy());
  }

  private ProcessInstanceTriggerEnvelope clientNonEntryEnvelope(boolean sigVerified, String keyId) {
    return new ProcessInstanceTriggerEnvelope(externalTaskResponseTrigger(), sigVerified, keyId);
  }

  private ContinueFlowElementTriggerDTO continueFlowElementTrigger() {
    return new ContinueFlowElementTriggerDTO(
        UUID.randomUUID(), List.of(1L), "flow-1", VariablesDTO.empty());
  }

  private ExternalTaskResponseTriggerDTO externalTaskResponseTrigger() {
    return new ExternalTaskResponseTriggerDTO(
        UUID.randomUUID(),
        List.of(1L),
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
        VariablesDTO.empty());
  }

  private SetVariableTriggerDTO setVariableTrigger() {
    return new SetVariableTriggerDTO(UUID.randomUUID(), List.of(1L), VariablesDTO.empty());
  }

  private StartCommandDTO startCommand() {
    return new StartCommandDTO(
        UUID.randomUUID(),
        null,
        null,
        new ProcessDefinitionKey("my-proc", 1),
        VariablesDTO.empty());
  }

  private AbortTriggerDTO abortTrigger() {
    return new AbortTriggerDTO(UUID.randomUUID(), List.of());
  }

  private RecordHeaders sigHeaders(String keyId) {
    RecordHeaders h = new RecordHeaders();
    h.add("X-TaktX-Signature", (keyId + ".FAKESIG").getBytes(StandardCharsets.UTF_8));
    return h;
  }

  private RecordHeaders noHeaders() {
    return new RecordHeaders();
  }

  private String buildJwt(String action, String processDefinitionId) {
    var b =
        Jwts.builder()
            .header()
            .keyId(PLATFORM_KID)
            .and()
            .subject("user-matrix")
            .issuer(ISSUER)
            .claim("action", action)
            .claim("version", -1)
            .claim("namespaceId", UUID.randomUUID().toString())
            .claim("auditId", UUID.randomUUID().toString())
            .expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(rsaKeyPair.getPrivate());
    if (processDefinitionId != null) b.claim("processDefinitionId", processDefinitionId);
    return b.compact();
  }

  private RecordHeaders headersWithJwt(String jwt) {
    RecordHeaders h = new RecordHeaders();
    h.add("X-TaktX-Authorization", jwt.getBytes(StandardCharsets.UTF_8));
    return h;
  }

  private RecordHeaders sigAndJwtHeaders(String keyId, String jwt) {
    RecordHeaders h = new RecordHeaders();
    h.add("X-TaktX-Signature", (keyId + ".FAKESIG").getBytes(StandardCharsets.UTF_8));
    h.add("X-TaktX-Authorization", jwt.getBytes(StandardCharsets.UTF_8));
    return h;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 1 — C-ERA=F, C-SE=F → non-entry, no sig → null
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row1_bothDisabled_nonEntry_noSig_returnsNull() {
    EngineAuthorizationService svc = service(false, false);
    assertThat(svc.authorize(noHeaders(), clientNonEntryEnvelope(false, null))).isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 2 — C-ERA=F, C-SE=F → non-entry, sig present → null (sig ignored)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row2_bothDisabled_nonEntry_sigPresent_sigIgnored_returnsNull() {
    EngineAuthorizationService svc = service(false, false);
    assertThat(
            svc.authorize(sigHeaders(WORKER_KEY_ID), clientNonEntryEnvelope(true, WORKER_KEY_ID)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 3 — C-ERA=F, C-SE=F → entry, no sig → null (auth not required)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row3_bothDisabled_entry_noSig_returnsNull() {
    EngineAuthorizationService svc = service(false, false);
    assertThat(
            svc.authorize(
                noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 6 — C-ERA=F, C-SE=T → signing is independently active
  //         non-entry, NO sig → THROW (missing required signature)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row6_signingOnly_nonEntry_noSig_throws_missingSignatureHeader() {
    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), clientNonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 7 — C-ERA=F, C-SE=T → signing is independently active
  //         non-entry, sig present → verify Ed25519 and return trust metadata
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row7_signingOnly_nonEntry_sigPresent_verifies_ed25519() {
    EngineAuthorizationService svc = service(false, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), clientNonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result).isNotNull();
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("billing-worker");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8a — C-ERA=F, C-SE=T → signing active
  //          entry, NO sig → throw (signing gate applies to entry commands too)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8a_signingOnly_entry_noSig_throws() {
    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8b — C-ERA=F, C-SE=T → signing active
  //          entry, ENGINE-role sig present → verify (ENGINE-role path)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8b_signingOnly_entry_engineSig_verifiedViaEngineRole() {
    EngineAuthorizationService svc = service(false, true);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(ENGINE_KEY_ID),
            new ProcessInstanceTriggerEnvelope(startCommand(), true, ENGINE_KEY_ID));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(ENGINE_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("engine");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8c — C-ERA=F, C-SE=T → signing active, auth gate off
  //          entry, CLIENT-role sig → verify / SIGNATURE_VERIFIED
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8c_signingOnly_entry_workerSig_accepted_signatureVerified() {
    EngineAuthorizationService svc = service(false, true);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(WORKER_KEY_ID),
            new ProcessInstanceTriggerEnvelope(startCommand(), true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 10 — C-ERA=T, C-SE=F → auth active, signing off
  //          non-entry, no sig → throw (signed non-entry required)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row10_authOnly_nonEntry_noSig_throws() {
    EngineAuthorizationService svc = service(true, false);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), clientNonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 11 — C-ERA=T, C-SE=F → auth active, signing off
  //          non-entry, sig present → verify Ed25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row11_authOnly_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, false);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), clientNonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 12 — C-ERA=T, C-SE=T → both active
  //          non-entry, no sig → throw (missing required sig)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row12_bothActive_nonEntry_noSig_throws() {
    EngineAuthorizationService svc = service(true, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), clientNonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 13 — C-ERA=T, C-SE=T → both active
  //          non-entry, sig present → verify Ed25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row13_bothActive_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), clientNonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 14 — C-ERA=T, C-SE=T → both active
  //          non-entry, no sig → throw (signing enforced)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row14_bothActive_nonEntry_noSig_throws() {
    EngineAuthorizationService svc = service(true, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), clientNonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 15 — C-ERA=T, C-SE=T → both active
  //          non-entry, sig present → verify Ed25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row15_bothActive_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), clientNonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  @Test
  void engineOnlyContinuation_clientSignatureRejected() {
    EngineAuthorizationService svc = service(true, true);

    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(WORKER_KEY_ID),
                    new ProcessInstanceTriggerEnvelope(
                        continueFlowElementTrigger(), true, WORKER_KEY_ID)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("not trusted for ENGINE process-instance command");
  }

  @Test
  void engineOnlyContinuation_engineSignatureAccepted() {
    EngineAuthorizationService svc = service(true, true);

    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(ENGINE_KEY_ID),
            new ProcessInstanceTriggerEnvelope(continueFlowElementTrigger(), true, ENGINE_KEY_ID));

    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(ENGINE_KEY_ID);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 18 — C-ERA=T, C-SE=F → auth active
  //          entry, no headers → throw (neither JWT nor ENGINE-sig present)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row18_authActive_entry_noHeaders_throws_missingAuthOrSig() {
    EngineAuthorizationService svc = service(true, false);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 19 — C-ERA=T → auth active
  //          entry, ENGINE-role Ed25519 sig → verify and accept
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row19_authActive_entry_engineSig_accepted() {
    EngineAuthorizationService svc = service(true, false);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(ENGINE_KEY_ID),
            new ProcessInstanceTriggerEnvelope(startCommand(), true, ENGINE_KEY_ID));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(ENGINE_KEY_ID);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 20 — C-ERA=T, C-SE=F → auth active, signing off
  //          entry, CLIENT-role sig, no JWT → throw (CLIENT satisfies signing but not auth gate)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row20_authActive_entry_workerSig_rejected_jwtRequired() {
    EngineAuthorizationService svc = service(true, false);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(WORKER_KEY_ID),
                    new ProcessInstanceTriggerEnvelope(startCommand(), true, WORKER_KEY_ID)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("JWT");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Additional: abort trigger follows same entry-command path
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void abort_signingOnly_entry_noSig_throws() {
    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(abortTrigger(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  @Test
  void abort_authActive_entry_noHeaders_throws() {
    EngineAuthorizationService svc = service(true, false);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(abortTrigger(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Rows 21-24: AND logic — both auth and signing active for entry commands
  // ══════════════════════════════════════════════════════════════════════════

  // Row 21 — C-ERA=T, C-SE=T
  //          entry, JWT present, sig ABSENT → throw (signing gate: sig required)
  @Test
  void row21_bothActive_entry_jwtOnly_throws_missingSignature() {
    EngineAuthorizationService svc = service(true, true);
    String jwt = buildJwt("START", "my-proc");
    assertThatThrownBy(
            () ->
                svc.authorize(
                    headersWithJwt(jwt),
                    new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // Row 22 — C-ERA=T, C-SE=T
  //          entry, JWT + CLIENT sig → BOTH verified; JWT_AND_ED25519 with signer info
  @Test
  void row22_bothActive_entry_jwtAndClientSig_bothVerified_combinedMetadata() {
    EngineAuthorizationService svc = service(true, true);
    String jwt = buildJwt("START", "my-proc");
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigAndJwtHeaders(WORKER_KEY_ID, jwt),
            new ProcessInstanceTriggerEnvelope(startCommand(), true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT_AND_ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-matrix");
    assertThat(result.getIssuer()).isEqualTo(ISSUER);
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("billing-worker");
  }

  // Row 23 — C-ERA=T, C-SE=T
  //          entry, ENGINE sig only → ENGINE_SIGNED (satisfies BOTH auth and signing gates)
  @Test
  void row23_bothActive_entry_engineSigOnly_satisfiesBothGates_engineSigned() {
    EngineAuthorizationService svc = service(true, true);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(ENGINE_KEY_ID),
            new ProcessInstanceTriggerEnvelope(startCommand(), true, ENGINE_KEY_ID));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(ENGINE_KEY_ID);
  }

  // Row 24 — C-ERA=T, C-SE=T
  //          entry, CLIENT sig only → throw (satisfies signing gate but not auth gate; JWT missing)
  @Test
  void row24_bothActive_entry_clientSigOnly_throws_jwtRequired() {
    EngineAuthorizationService svc = service(true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(WORKER_KEY_ID),
                    new ProcessInstanceTriggerEnvelope(startCommand(), true, WORKER_KEY_ID)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("JWT");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Revoked key must be rejected even when signing gate is active
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void revokedKey_nonEntry_sigPresent_throws() {
    String revokedKeyId = "revoked-key-001";
    when(signingKeysStore.get(revokedKeyId))
        .thenReturn(
            SigningKeyDTO.builder()
                .keyId(revokedKeyId)
                .publicKeyBase64("dummy")
                .status(KeyStatus.REVOKED)
                .owner("former-worker")
                .role(KeyRole.CLIENT)
                .build());

    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(revokedKeyId),
                    new ProcessInstanceTriggerEnvelope(
                        continueFlowElementTrigger(), true, revokedKeyId)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Revoked");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Unverified signature (deserializer error) must be rejected
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void signatureError_nonEntry_signingActive_throws_signatureError() {
    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(WORKER_KEY_ID),
                    new ProcessInstanceTriggerEnvelope(
                        continueFlowElementTrigger(),
                        false,
                        WORKER_KEY_ID,
                        "Malformed base64 signature for keyId=" + WORKER_KEY_ID)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row S1 — C-ERA=F, C-SE=T → SetVariableTriggerDTO is an entry command
  //          signing gate active, no sig → throw
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void rowS1_setVariable_signingOnly_noSig_throws() {
    EngineAuthorizationService svc = service(false, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(),
                    new ProcessInstanceTriggerEnvelope(setVariableTrigger(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row S2 — C-ERA=T, C-SE=F → SetVariableTriggerDTO is an entry command
  //          auth gate active, no JWT / no ENGINE-sig → throw
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void rowS2_setVariable_authActive_noHeaders_throws() {
    EngineAuthorizationService svc = service(true, false);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(),
                    new ProcessInstanceTriggerEnvelope(setVariableTrigger(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row S3 — C-ERA=T, C-SE=F → SetVariableTriggerDTO is an entry command
  //          auth gate active, ENGINE-role sig → ENGINE_SIGNED (satisfies auth gate)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void rowS3_setVariable_authActive_engineSig_accepted() {
    EngineAuthorizationService svc = service(true, false);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigHeaders(ENGINE_KEY_ID),
            new ProcessInstanceTriggerEnvelope(setVariableTrigger(), true, ENGINE_KEY_ID));
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.ENGINE_SIGNED);
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(ENGINE_KEY_ID);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row S4 — C-ERA=T, C-SE=T → SetVariableTriggerDTO is an entry command
  //          both gates active; JWT (SET_VARIABLE action) + CLIENT sig → JWT_AND_ED25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void rowS4_setVariable_bothActive_jwtAndClientSig_bothVerified() {
    EngineAuthorizationService svc = service(true, true);
    String jwt = buildJwt("SET_VARIABLE", null);
    CommandTrustMetadataDTO result =
        svc.authorize(
            sigAndJwtHeaders(WORKER_KEY_ID, jwt),
            new ProcessInstanceTriggerEnvelope(setVariableTrigger(), true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT_AND_ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.JWT_AUTHORIZED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getUserId()).isEqualTo("user-matrix");
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("billing-worker");
  }
}
