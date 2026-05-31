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
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityMode;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.lang.reflect.Field;
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
  void refreshWorkerSigningFunctionRegistration_registersSignerAfterRuntimeEnablement() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

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

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
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
  void requestedSecuredPolicy_preparesButDoesNotActivateClientSigning() throws Exception {
    TaktXClient client = clientWithSigningIdentity();
    setNamespaceSecurityPolicies(client, requestedPolicy(), null);

    assertThat(client.shouldPrepareSigningInfrastructure()).isTrue();
    assertThat(client.shouldSignClientMessages()).isFalse();

    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNull();
  }

  @Test
  void activeSecuredPolicy_reactivatesClientSigningWithoutLegacyRuntimeToggle() throws Exception {
    TaktXClient client = clientWithSigningIdentity();
    setNamespaceSecurityPolicies(client, activeSigningPolicy(), activeSigningPolicy());

    assertThat(client.shouldPrepareSigningInfrastructure()).isTrue();
    assertThat(client.shouldSignClientMessages()).isTrue();

    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_keepsSignerInactiveWhileRuntimeSigningDisabled() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();

    RuntimeConfigurationHolder.clear();

    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .isNull();
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_doesNotOverrideExistingGlobalSigner() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

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

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isSameAs(existingGlobalSigner);
  }

  private static TaktXClient clientWithSigningIdentity() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

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

  private static NamespaceSecurityPolicyDTO requestedPolicy() {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.SECURED)
        .activationState(SecurityActivationState.REQUESTED)
        .desiredPolicyVersion(42L)
        .desiredPolicyHash("desired-42")
        .requiredSigning(RequiredSigningDTO.builder().workerResponses(true).build())
        .requiredAuthorization(RequiredAuthorizationDTO.builder().build())
        .build();
  }

  private static NamespaceSecurityPolicyDTO activeSigningPolicy() {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.SECURED)
        .activationState(SecurityActivationState.ACTIVE)
        .desiredPolicyVersion(42L)
        .desiredPolicyHash("policy-42")
        .activePolicyVersion(42L)
        .activePolicyHash("policy-42")
        .requiredSigning(RequiredSigningDTO.builder().workerResponses(true).build())
        .requiredAuthorization(RequiredAuthorizationDTO.builder().build())
        .build();
  }
}
