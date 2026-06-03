/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TaktXClientWorkerSigningTest {

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
    SigningServiceHolder.clear();
  }

  @Test
  void
      refreshWorkerSigningFunctionRegistration_ignoresLegacyRuntimeEnablementWithoutAnchoredPolicy() {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    assertThat(SigningServiceHolder.get()).isNull();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNull();
  }

  @Test
  void shouldKeepDefaultOpenClientSigningInactive() {
    TaktXClient client = clientWithSigningIdentity();

    assertThat(client.shouldPrepareSigningInfrastructure()).isFalse();
    assertThat(client.shouldSignClientMessages()).isFalse();

    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNull();
  }

  @Test
  void currentAnchoredPolicy_preparesAndActivatesClientSigning() throws Exception {
    TaktXClient client = clientWithSigningIdentity();
    setNamespaceSecurityPolicies(client, anchoredPolicy(42L, "policy-42"), null);

    assertThat(client.shouldPrepareSigningInfrastructure()).isTrue();
    assertThat(client.shouldSignClientMessages()).isTrue();

    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void activeAnchoredPolicy_reactivatesClientSigningWithoutLegacyRuntimeToggle() throws Exception {
    TaktXClient client = clientWithSigningIdentity();
    NamespaceSecurityPolicyDTO anchored = anchoredPolicy(42L, "policy-42");
    setNamespaceSecurityPolicies(client, anchored, anchored);

    assertThat(client.shouldPrepareSigningInfrastructure()).isTrue();
    assertThat(client.shouldSignClientMessages()).isTrue();

    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_keepsSignerActiveWithoutLegacyRuntimeConfiguration()
      throws Exception {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    NamespaceSecurityPolicyDTO anchored = anchoredPolicy(44L, "policy-44");
    setNamespaceSecurityPolicies(client, anchored, anchored);

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();

    RuntimeConfigurationHolder.clear();

    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_doesNotOverrideExistingGlobalSigner() {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    SigningServiceHolder.SigningFunction existingGlobalSigner = payload -> "engine-key.stub";
    SigningServiceHolder.set(existingGlobalSigner);

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    try {
      NamespaceSecurityPolicyDTO anchored = anchoredPolicy(43L, "policy-43");
      setNamespaceSecurityPolicies(client, anchored, anchored);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isSameAs(existingGlobalSigner);
  }

  private static TaktXClient clientWithSigningIdentity() {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    return TaktXClient.newClientBuilder()
        .withProperties(props)
        .withSigningIdentitySource(() -> signingIdentity)
        .build();
  }

  private static void setNamespaceSecurityPolicies(
      TaktXClient client,
      NamespaceSecurityPolicyDTO currentPolicy,
      NamespaceSecurityPolicyDTO activePolicy)
      throws Exception {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    store.setCurrentPolicy(currentPolicy);
    store.setActivePolicy(activePolicy);

    Field field = TaktXClient.class.getDeclaredField("namespaceSecurityPolicyStore");
    field.setAccessible(true);
    field.set(client, store);
  }

  private static NamespaceSecurityPolicyDTO anchoredPolicy(long version, String hash) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.ANCHORED)
        .policyVersion(version)
        .policyHash(hash)
        .build();
  }

  private static SigningIdentity signingIdentity(String keyId) {
    KeyPair keyPair = SigningKeyGenerator.generate();
    return SigningIdentity.ed25519(
        keyId,
        SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
        SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
  }
}
