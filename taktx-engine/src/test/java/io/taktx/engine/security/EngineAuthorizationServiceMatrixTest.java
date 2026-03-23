/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
import io.taktx.engine.license.LicenseManager;
import io.taktx.engine.license.LicenseState;
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
 *  C-ERA = config.engineRequiresAuthorization
 *  C-SE  = config.signingEnabled
 *  L-ERA = license.isEngineRequiresAuthorization
 *  L-ESA = license.isEventSigningAllowed
 *
 * ┌────┬───────┬──────┬───────┬───────┬───────────┬───────────────┬──────────┬──────────────────────────────────────────────┐
 * │ #  │ C-ERA │ C-SE │ L-ERA │ L-ESA │ Cmd type  │ Sig hdr(role) │ JWT hdr  │ Expected                                     │
 * ├────┼───────┼──────┼───────┼───────┼───────────┼───────────────┼──────────┼──────────────────────────────────────────────┤
 * │  1 │ false │false │  any  │  any  │ non-entry │ absent        │ absent   │ null                                         │
 * │  2 │ false │false │  any  │  any  │ non-entry │ CLIENT        │ absent   │ null (sig ignored)                           │
 * │  3 │ false │false │  any  │  any  │ entry     │ absent        │ absent   │ null (no auth)                               │
 * │  4 │ false │true  │  any  │ false │ non-entry │ absent        │ absent   │ null (signing disabled by license)           │
 * │  5 │ false │true  │  any  │ false │ non-entry │ CLIENT        │ absent   │ null (signing disabled by license)           │
 * │  6 │ false │true  │  any  │ true  │ non-entry │ absent        │ absent   │ throw (missing sig)                ← fixed   │
 * │  7 │ false │true  │  any  │ true  │ non-entry │ CLIENT        │ absent   │ verify Ed25519 / SIGNATURE_VERIFIED← fixed   │
 * │  8 │ false │true  │  any  │ false │ entry     │ absent        │ absent   │ null (signing blocked by license)            │
 * │  8a│ false │true  │  any  │ true  │ entry     │ absent        │ absent   │ throw (signing requires sig on entry too)    │
 * │  8b│ false │true  │  any  │ true  │ entry     │ ENGINE        │ absent   │ verify / ENGINE_SIGNED                       │
 * │  8c│ false │true  │  any  │ true  │ entry     │ CLIENT        │ absent   │ verify / SIGNATURE_VERIFIED (AND: auth off)  │
 * │  9 │ true  │false │ false │  any  │ non-entry │ absent        │ absent   │ null (auth disabled by license)              │
 * │ 10 │ true  │false │ true  │  any  │ non-entry │ absent        │ absent   │ current trust metadata                       │
 * │ 11 │ true  │false │ true  │  any  │ non-entry │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 12 │ true  │true  │ true  │ true  │ non-entry │ absent        │ absent   │ throw (missing sig)                          │
 * │ 13 │ true  │true  │ true  │ true  │ non-entry │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 14 │ true  │true  │ false │ true  │ non-entry │ absent        │ absent   │ throw (signing active via C-SE+L-ESA)        │
 * │ 15 │ true  │true  │ false │ true  │ non-entry │ CLIENT        │ absent   │ verify Ed25519                               │
 * │ 16 │ true  │true  │ true  │ false │ non-entry │ absent        │ absent   │ current trust metadata                       │
 * │ 17 │ true  │false │ false │  any  │ entry     │ absent        │ absent   │ null (auth disabled by license)              │
 * │ 18 │ true  │false │ true  │  any  │ entry     │ absent        │ absent   │ throw (missing JWT/sig)                      │
 * │ 19 │ true  │false │ true  │  any  │ entry     │ ENGINE        │ absent   │ ENGINE_SIGNED (ENGINE satisfies auth gate)   │
 * │ 20 │ true  │false │ true  │  any  │ entry     │ CLIENT        │ absent   │ throw (CLIENT ≠ ENGINE, JWT missing)         │
 * │ 21 │ true  │true  │ true  │ true  │ entry     │ absent        │ present  │ throw (signing gate: missing Ed25519)        │
 * │ 22 │ true  │true  │ true  │ true  │ entry     │ CLIENT        │ present  │ JWT_AUTHORIZED + signer info (AND: both pass)│
 * │ 23 │ true  │true  │ true  │ true  │ entry     │ ENGINE        │ absent   │ ENGINE_SIGNED (ENGINE satisfies both gates)  │
 * │ 24 │ true  │true  │ true  │ true  │ entry     │ CLIENT        │ absent   │ throw (CLIENT satisfies signing, not auth)   │
 * └────┴───────┴──────┴───────┴───────┴───────────┴───────────────┴──────────┴──────────────────────────────────────────────┘
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

  /** Builds a service with explicit config and license flags. */
  private EngineAuthorizationService service(
      boolean cEra, boolean cSe, boolean lEra, boolean lEsa) {
    globalConfigStore.update(
        GlobalConfigurationDTO.builder()
            .engineRequiresAuthorization(cEra)
            .signingEnabled(cSe)
            .build());
    LicenseManager license = licenseMock(lEra, lEsa);
    return new EngineAuthorizationService(
        taktConfig,
        globalConfigStore,
        publicKeyProvider,
        nonceStore,
        kafkaStreams,
        license,
        new OpenKeyTrustPolicy());
  }

  private static LicenseManager licenseMock(boolean era, boolean esa) {
    return new LicenseManager() {
      @Override
      public LicenseState getLicenseState() {
        return LicenseState.VALID;
      }

      @Override
      public String getLicenseInfo() {
        return "matrix-test";
      }

      @Override
      public int getPartitionBudget() {
        return Integer.MAX_VALUE;
      }

      @Override
      public boolean isEventSigningAllowed() {
        return esa;
      }

      @Override
      public boolean isEngineRequiresAuthorization() {
        return era;
      }

      @Override
      public void updateFromLicensePush(
          String licenseType,
          Integer partitionBudget,
          boolean eventSigning,
          boolean commandAuthorization) {
        // No-op: test stub — license state is fixed at construction time
      }
    };
  }

  private ProcessInstanceTriggerEnvelope nonEntryEnvelope(boolean sigVerified, String keyId) {
    return new ProcessInstanceTriggerEnvelope(setVariableTrigger(), sigVerified, keyId);
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
    EngineAuthorizationService svc = service(false, false, true, true);
    assertThat(svc.authorize(noHeaders(), nonEntryEnvelope(false, null))).isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 2 — C-ERA=F, C-SE=F → non-entry, sig present → null (sig ignored)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row2_bothDisabled_nonEntry_sigPresent_sigIgnored_returnsNull() {
    EngineAuthorizationService svc = service(false, false, true, true);
    assertThat(svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 3 — C-ERA=F, C-SE=F → entry, no sig → null (auth not required)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row3_bothDisabled_entry_noSig_returnsNull() {
    EngineAuthorizationService svc = service(false, false, true, true);
    assertThat(
            svc.authorize(
                noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 4 — C-ERA=F, C-SE=T, L-ESA=F → signing blocked by license
  //         non-entry, no sig → null (signing disabled by license)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row4_signingEnabled_licenseBlocksSigning_nonEntry_noSig_returnsNull() {
    EngineAuthorizationService svc = service(false, true, true, false /* L-ESA=false */);
    assertThat(svc.authorize(noHeaders(), nonEntryEnvelope(false, null))).isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 5 — C-ERA=F, C-SE=T, L-ESA=F → signing blocked by license
  //         non-entry, sig present → null (signing disabled by license, sig ignored)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row5_signingEnabled_licenseBlocksSigning_nonEntry_sigPresent_returnsNull() {
    EngineAuthorizationService svc = service(false, true, true, false /* L-ESA=false */);
    // Even though sig is present it is not verified when signing is blocked by license
    assertThat(svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 6 — C-ERA=F, C-SE=T, L-ESA=T → signing is independently active
  //         non-entry, NO sig → THROW (missing required signature)
  // BUG that was fixed: previously returned null because C-ERA=false caused early exit
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row6_signingOnly_nonEntry_noSig_throws_missingSignatureHeader() {
    EngineAuthorizationService svc = service(false, true, true, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), nonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 7 — C-ERA=F, C-SE=T, L-ESA=T → signing is independently active
  //         non-entry, sig present → verify Ed25519 and return trust metadata
  // BUG that was fixed: previously returned null because C-ERA=false caused early exit
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row7_signingOnly_nonEntry_sigPresent_verifies_ed25519() {
    EngineAuthorizationService svc = service(false, true, true, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result).isNotNull();
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("billing-worker");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8 — C-ERA=F, C-SE=T, L-ESA=F → signing blocked by license
  //         entry, no headers → null
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8_signingLicenseBlocked_entry_noHeaders_returnsNull() {
    EngineAuthorizationService svc = service(false, true, true, false /* L-ESA=false */);
    assertThat(
            svc.authorize(
                noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8a — C-ERA=F, C-SE=T, L-ESA=T → signing active
  //          entry, NO sig → throw (signing gate applies to entry commands too)
  //          External clients must sign their start commands when signingEnabled=true.
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8a_signingOnly_entry_noSig_throws() {
    EngineAuthorizationService svc = service(false, true, true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 8b — C-ERA=F, C-SE=T, L-ESA=T → signing active
  //          entry, ENGINE-role sig present → verify (ENGINE-role path)
  //          NEW: engine-internal entry commands are verified even without C-ERA
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8b_signingOnly_entry_engineSig_verifiedViaEngineRole() {
    EngineAuthorizationService svc = service(false, true, true, true);
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
  // Row 8c — C-ERA=F, C-SE=T, L-ESA=T → signing active, auth gate off
  //          entry, CLIENT-role sig → verify / SIGNATURE_VERIFIED
  //          When auth is off, any valid Ed25519 key (including CLIENT) satisfies the signing gate.
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row8c_signingOnly_entry_workerSig_accepted_signatureVerified() {
    EngineAuthorizationService svc = service(false, true, true, true);
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
  // Row 9 — C-ERA=T, C-SE=F, L-ERA=F → auth blocked by license
  //         non-entry, no sig → null (warn + pass)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row9_authEnabled_licenseBlocksAuth_nonEntry_noSig_returnsNull() {
    EngineAuthorizationService svc = service(true, false, false /* L-ERA=false */, true);
    assertThat(svc.authorize(noHeaders(), nonEntryEnvelope(false, null))).isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 10 — C-ERA=T, C-SE=F, L-ERA=T → auth active, signing off
  //          non-entry, no sig → return embedded trust metadata
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row10_authOnly_nonEntry_noSig_returnsCurrentTrustMetadata() {
    EngineAuthorizationService svc = service(true, false, true, true);

    CommandTrustMetadataDTO embedded =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("user-1")
            .issuer("taktx-platform")
            .build();
    SetVariableTriggerDTO trigger = setVariableTrigger();
    trigger.setCurrentTrustMetadata(embedded);

    CommandTrustMetadataDTO result =
        svc.authorize(noHeaders(), new ProcessInstanceTriggerEnvelope(trigger, false, null));
    assertThat(result).isEqualTo(embedded);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 11 — C-ERA=T, C-SE=F, L-ERA=T → auth active, signing off
  //          non-entry, sig present → verify Ed25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row11_authOnly_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, false, true, false /* L-ESA doesn't matter */);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 12 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T → both active
  //          non-entry, no sig → throw (missing required sig)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row12_bothActive_nonEntry_noSig_throws() {
    EngineAuthorizationService svc = service(true, true, true, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), nonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 13 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T → both active
  //          non-entry, sig present → verify Ed25519
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row13_bothActive_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, true, true, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 14 — C-ERA=T, C-SE=T, L-ERA=F, L-ESA=T → auth blocked by license, signing still active
  //          non-entry, no sig → throw (signing enforced by C-SE + L-ESA)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row14_authLicenseBlocked_signingActive_nonEntry_noSig_throws() {
    EngineAuthorizationService svc = service(true, true, false /* L-ERA=false */, true);
    assertThatThrownBy(() -> svc.authorize(noHeaders(), nonEntryEnvelope(false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 15 — C-ERA=T, C-SE=T, L-ERA=F, L-ESA=T → auth blocked by license, signing still active
  //          non-entry, sig present → verify Ed25519 (signing gate handles it)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row15_authLicenseBlocked_signingActive_nonEntry_sigPresent_verifiesEd25519() {
    EngineAuthorizationService svc = service(true, true, false /* L-ERA=false */, true);
    CommandTrustMetadataDTO result =
        svc.authorize(sigHeaders(WORKER_KEY_ID), nonEntryEnvelope(true, WORKER_KEY_ID));
    assertThat(result.getAuthMethod()).isEqualTo(CommandAuthMethod.ED25519);
    assertThat(result.getVerificationResult())
        .isEqualTo(CommandTrustVerificationResult.SIGNATURE_VERIFIED);
    assertThat(result.getTrusted()).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 16 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=F → signing blocked by license, auth active
  //          non-entry, no sig → auth active, signing off → return current trust metadata
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row16_signingLicenseBlocked_authActive_nonEntry_noSig_returnsCurrentTrustMetadata() {
    EngineAuthorizationService svc = service(true, true, true, false /* L-ESA=false */);

    CommandTrustMetadataDTO embedded =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("user-x")
            .issuer("taktx-platform")
            .build();
    SetVariableTriggerDTO trigger = setVariableTrigger();
    trigger.setCurrentTrustMetadata(embedded);

    CommandTrustMetadataDTO result =
        svc.authorize(noHeaders(), new ProcessInstanceTriggerEnvelope(trigger, false, null));
    assertThat(result).isEqualTo(embedded);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 17 — C-ERA=T, C-SE=F, L-ERA=F → entry auth blocked by license
  //          entry, no headers → null (warn + accept)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row17_entryAuth_licenseBlocksAuth_entry_noHeaders_returnsNull() {
    EngineAuthorizationService svc = service(true, false, false /* L-ERA=false */, true);
    assertThat(
            svc.authorize(
                noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isNull();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 18 — C-ERA=T, C-SE=F, L-ERA=T → auth active
  //          entry, no headers → throw (neither JWT nor ENGINE-sig present)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row18_authActive_entry_noHeaders_throws_missingAuthOrSig() {
    EngineAuthorizationService svc = service(true, false, true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Entry command");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Row 19 — C-ERA=T, L-ERA=T → auth active
  //          entry, ENGINE-role Ed25519 sig → verify and accept
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row19_authActive_entry_engineSig_accepted() {
    EngineAuthorizationService svc = service(true, false, true, true);
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
  // Row 20 — C-ERA=T, C-SE=F, L-ERA=T → auth active, signing off
  //          entry, CLIENT-role sig, no JWT → throw (CLIENT satisfies signing but not auth gate)
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void row20_authActive_entry_workerSig_rejected_jwtRequired() {
    EngineAuthorizationService svc = service(true, false, true, true);
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
    // signingEnabled=true requires Ed25519 on entry commands; no sig → throw
    EngineAuthorizationService svc = service(false, true, true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    noHeaders(), new ProcessInstanceTriggerEnvelope(abortTrigger(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  @Test
  void abort_authActive_entry_noHeaders_throws() {
    EngineAuthorizationService svc = service(true, false, true, true);
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

  // Row 21 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T
  //          entry, JWT present, sig ABSENT → throw (signing gate: sig required)
  @Test
  void row21_bothActive_entry_jwtOnly_throws_missingSignature() {
    EngineAuthorizationService svc = service(true, true, true, true);
    String jwt = buildJwt("START", "my-proc");
    assertThatThrownBy(
            () ->
                svc.authorize(
                    headersWithJwt(jwt),
                    new ProcessInstanceTriggerEnvelope(startCommand(), false, null)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("X-TaktX-Signature");
  }

  // Row 22 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T
  //          entry, JWT + CLIENT sig → BOTH verified; JWT_AND_ED25519 with signer info
  @Test
  void row22_bothActive_entry_jwtAndClientSig_bothVerified_combinedMetadata() {
    EngineAuthorizationService svc = service(true, true, true, true);
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
    // Ed25519 signer info is also present
    assertThat(result.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(result.getSignerOwner()).isEqualTo("billing-worker");
  }

  // Row 23 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T
  //          entry, ENGINE sig only → ENGINE_SIGNED (satisfies BOTH auth and signing gates)
  @Test
  void row23_bothActive_entry_engineSigOnly_satisfiesBothGates_engineSigned() {
    EngineAuthorizationService svc = service(true, true, true, true);
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

  // Row 24 — C-ERA=T, C-SE=T, L-ERA=T, L-ESA=T
  //          entry, CLIENT sig only → throw (satisfies signing gate but not auth gate; JWT missing)
  @Test
  void row24_bothActive_entry_clientSigOnly_throws_jwtRequired() {
    EngineAuthorizationService svc = service(true, true, true, true);
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

    EngineAuthorizationService svc = service(false, true, true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(revokedKeyId),
                    new ProcessInstanceTriggerEnvelope(setVariableTrigger(), true, revokedKeyId)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Revoked");
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Unverified signature (deserializer error) must be rejected
  // ══════════════════════════════════════════════════════════════════════════
  @Test
  void signatureError_nonEntry_signingActive_throws_signatureError() {
    EngineAuthorizationService svc = service(false, true, true, true);
    assertThatThrownBy(
            () ->
                svc.authorize(
                    sigHeaders(WORKER_KEY_ID),
                    new ProcessInstanceTriggerEnvelope(
                        setVariableTrigger(),
                        false,
                        WORKER_KEY_ID,
                        "Malformed base64 signature for keyId=" + WORKER_KEY_ID)))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("Malformed base64 signature");
  }
}
