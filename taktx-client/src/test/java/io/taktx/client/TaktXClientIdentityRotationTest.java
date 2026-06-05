/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TaktXClientIdentityRotationTest {

  @Test
  void currentSigningIdentity_surfacesUnexpectedChurnForNonRotatingSource() throws Exception {
    TaktXClient client =
        clientWithSource(
            new SequencedSigningIdentitySource(
                false,
                SigningIdentity.of("worker-a", "private-a", "public-a", "Ed25519"),
                SigningIdentity.of("worker-b", "private-b", "public-b", "Ed25519")));

    invokeCurrentSigningIdentity(client);
    invokeCurrentSigningIdentity(client);

    List<?> events = currentSecurityEvents(client);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).toString())
        .contains(SecurityPostureIssueCodes.UNEXPECTED_SIGNING_IDENTITY_CHURN)
        .contains("worker-a")
        .contains("worker-b");
  }

  @Test
  void currentSigningIdentity_surfacesExpectedRotationForLiveRotatingSource() throws Exception {
    TaktXClient client =
        clientWithSource(
            new SequencedSigningIdentitySource(
                true,
                SigningIdentity.of("worker-a", "private-a", "public-a", "Ed25519"),
                SigningIdentity.of("worker-b", "private-b", "public-b", "Ed25519")));

    invokeCurrentSigningIdentity(client);
    invokeCurrentSigningIdentity(client);

    List<?> events = currentSecurityEvents(client);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).toString())
        .contains(SecurityPostureIssueCodes.SIGNING_IDENTITY_ROTATED)
        .contains("worker-a")
        .contains("worker-b");
  }

  @Test
  void ensureWorkerKeyPublished_requiresRepublishWhenPublicKeyChangesUnderSameKeyId()
      throws Exception {
    TaktXClient client =
        clientWithSource(() -> SigningIdentity.of("worker-a", "private-a", "public-a", "Ed25519"));

    String originalDescriptor =
        (String)
            invokeDeclaredMethod(
                client,
                "workerIdentityPublicationDescriptor",
                new Class<?>[] {SigningIdentity.class},
                SigningIdentity.of("worker-a", "private-a", "public-a", "Ed25519"));

    String rotatedDescriptor =
        (String)
            invokeDeclaredMethod(
                client,
                "workerIdentityPublicationDescriptor",
                new Class<?>[] {SigningIdentity.class},
                SigningIdentity.of("worker-a", "private-a-2", "public-b", "Ed25519"));

    assertThat(originalDescriptor).isNotEqualTo(rotatedDescriptor);
    assertThat(originalDescriptor).contains("worker-a").contains("public-a");
    assertThat(rotatedDescriptor).contains("worker-a").contains("public-b");
  }

  private static TaktXClient clientWithSource(SigningIdentitySource source) {
    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    return TaktXClient.newClientBuilder()
        .withProperties(props)
        .withSigningIdentitySource(source)
        .build();
  }

  private static void setAnchoredPolicies(TaktXClient client) throws Exception {
    ClientNamespaceSecurityPolicyStore store = new ClientNamespaceSecurityPolicyStore();
    io.taktx.dto.NamespaceSecurityPolicyDTO anchored =
        io.taktx.dto.NamespaceSecurityPolicyDTO.builder()
            .mode(io.taktx.dto.SecurityMode.ANCHORED)
            .policyVersion(1L)
            .policyHash("anchored-policy-1")
            .build();
    store.setCurrentPolicy(anchored);
    store.setActivePolicy(anchored);
    setField(client, "namespaceSecurityPolicyStore", store);
  }

  private static Object invokeCurrentSigningIdentity(TaktXClient client) throws Exception {
    return invokeDeclaredMethod(client, "currentSigningIdentity", new Class<?>[0]);
  }

  private static Object invokeDeclaredMethod(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  @SuppressWarnings("unchecked")
  private static List<?> currentSecurityEvents(TaktXClient client) throws Exception {
    Method method = client.getClass().getDeclaredMethod("currentSecurityEventSnapshot");
    method.setAccessible(true);
    return (List<?>) method.invoke(client);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object getField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private static final class SequencedSigningIdentitySource implements SigningIdentitySource {
    private final boolean liveRotation;
    private final SigningIdentity[] identities;
    private int index;

    private SequencedSigningIdentitySource(boolean liveRotation, SigningIdentity... identities) {
      this.liveRotation = liveRotation;
      this.identities = identities;
    }

    @Override
    public SigningIdentity currentIdentity() {
      SigningIdentity identity = identities[Math.min(index, identities.length - 1)];
      index++;
      return identity;
    }

    @Override
    public boolean supportsLiveRotation() {
      return liveRotation;
    }
  }
}
