/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.taktx.dto.TokenClaims;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationTokenValidatorTest {

  private static final String TEST_KID = "platform-key-test";

  private PublicKey publicKey;
  private PrivateKey privateKey;
  private AuthorizationTokenValidator validator;

  @BeforeEach
  void setUp() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    publicKey = kp.getPublic();
    privateKey = kp.getPrivate();
    // key source resolves by kid — returns our test key for the expected kid
    validator = new AuthorizationTokenValidator(kid -> publicKey);
  }

  private String buildJwt(
      String action,
      String processDefinitionId,
      int version,
      String auditId,
      String userId,
      Date expiry) {
    return Jwts.builder()
        .header()
        .keyId(TEST_KID)
        .and()
        .subject(userId)
        .issuer("taktx-platform-service")
        .claim("action", action)
        .claim("processDefinitionId", processDefinitionId)
        .claim("version", version)
        .claim("namespaceId", UUID.randomUUID().toString())
        .claim("auditId", auditId)
        .expiration(expiry)
        .signWith(privateKey)
        .compact();
  }

  @Test
  void validToken_parsesClaimsCorrectly() {
    String auditId = UUID.randomUUID().toString();
    String jwt =
        buildJwt(
            "START",
            "loan-approval",
            3,
            auditId,
            "user-42",
            Date.from(Instant.now().plusSeconds(300)));

    TokenClaims claims = validator.validate(jwt);

    assertThat(claims.getUserId()).isEqualTo("user-42");
    assertThat(claims.getAction()).isEqualTo("START");
    assertThat(claims.getProcessDefinitionId()).isEqualTo("loan-approval");
    assertThat(claims.getVersion()).isEqualTo(3);
    assertThat(claims.getAuditId()).isEqualTo(auditId);
    assertThat(claims.getIssuer()).isEqualTo("taktx-platform-service");
  }

  @Test
  void expiredToken_throwsAuthorizationTokenException() {
    String jwt =
        buildJwt(
            "START",
            "loan-approval",
            1,
            UUID.randomUUID().toString(),
            "user",
            Date.from(Instant.now().minusSeconds(1)));

    assertThatThrownBy(() -> validator.validate(jwt))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void tamperedToken_throwsAuthorizationTokenException() {
    String jwt =
        buildJwt(
            "START",
            "loan-approval",
            1,
            UUID.randomUUID().toString(),
            "user",
            Date.from(Instant.now().plusSeconds(300)));

    // Tamper with signature
    String tampered = jwt.substring(0, jwt.lastIndexOf('.') + 1) + "invalidsignature";

    assertThatThrownBy(() -> validator.validate(tampered))
        .isInstanceOf(AuthorizationTokenException.class);
  }

  @Test
  void wrongKey_throwsAuthorizationTokenException() throws Exception {
    KeyPair otherKp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    String jwt =
        Jwts.builder()
            .header()
            .keyId(TEST_KID)
            .and()
            .subject("u")
            .issuer("taktx-platform-service")
            .expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(otherKp.getPrivate())
            .compact();

    assertThatThrownBy(() -> validator.validate(jwt))
        .isInstanceOf(AuthorizationTokenException.class);
  }

  @Test
  void nullToken_throwsIllegalArgument() {
    assertThatThrownBy(() -> validator.validate(null))
        .isInstanceOf(AuthorizationTokenException.class);
  }

  @Test
  void blankToken_throwsAuthorizationTokenException() {
    assertThatThrownBy(() -> validator.validate("  "))
        .isInstanceOf(AuthorizationTokenException.class);
  }

  @Test
  void missingKid_throwsAuthorizationTokenException() {
    // A JWT with no kid header must be rejected
    String jwt =
        Jwts.builder()
            .subject("u")
            .issuer("taktx-platform-service")
            .expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(privateKey)
            .compact();

    assertThatThrownBy(() -> validator.validate(jwt))
        .isInstanceOf(AuthorizationTokenException.class)
        .hasMessageContaining("kid");
  }
}
