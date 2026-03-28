/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import io.taktx.dto.TokenClaims;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Validates RS256 JWTs issued by the Platform Service.
 *
 * <p>Framework-agnostic — no CDI or Spring annotations. Callers provide a {@link PublicKeySource}
 * that resolves a key by the JWT {@code kid} header. Works in both Quarkus (engine) and Spring
 * (Ingester).
 */
public class AuthorizationTokenValidator {

  private final PublicKeySource keySource;

  public AuthorizationTokenValidator(PublicKeySource keySource) {
    this.keySource = keySource;
  }

  /**
   * Validates the raw JWT string and returns the parsed claims.
   *
   * @param rawJwt the compact serialised JWT
   * @return parsed {@link TokenClaims}
   * @throws AuthorizationTokenException if signature, expiry, or mandatory claims are invalid
   */
  public TokenClaims validate(String rawJwt) {
    if (rawJwt == null || rawJwt.isBlank()) {
      throw new AuthorizationTokenException("JWT must not be null or blank");
    }

    // Peek at the kid header before signature verification to resolve the correct key
    String kid = peekKid(rawJwt);
    PublicKey publicKey = keySource.getKey(kid);

    try {
      Claims claims =
          Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(rawJwt).getPayload();

      return mapClaims(claims);

    } catch (ExpiredJwtException e) {
      throw new AuthorizationTokenException("Token expired", e);
    } catch (SignatureException e) {
      throw new AuthorizationTokenException("Invalid token signature", e);
    } catch (JwtException e) {
      throw new AuthorizationTokenException("Invalid token: " + e.getMessage(), e);
    }
  }

  private TokenClaims mapClaims(Claims claims) {
    String namespaceIdStr = claims.get("namespaceId", String.class);
    String auditIdStr = claims.get("auditId", String.class);
    String processDefinitionId = claims.get("processDefinitionId", String.class);
    Integer version = claims.get("version", Integer.class);

    return TokenClaims.builder()
        .userId(claims.getSubject())
        .issuer(claims.getIssuer())
        .action(claims.get("action", String.class))
        .namespaceId(namespaceIdStr != null ? UUID.fromString(namespaceIdStr) : null)
        .auditId(auditIdStr)
        .processDefinitionId(processDefinitionId)
        .version(version != null ? version : -1)
        .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now())
        .expiresAt(claims.getExpiration() != null ? claims.getExpiration().toInstant() : null)
        .build();
  }

  /** Extracts the {@code kid} header without verifying the signature. */
  private static String peekKid(String rawJwt) {
    try {
      String[] parts = rawJwt.split("\\.");
      if (parts.length < 2) throw new AuthorizationTokenException("Malformed JWT");
      String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
      int kidStart = headerJson.indexOf("\"kid\"");
      if (kidStart < 0) throw new AuthorizationTokenException("Missing 'kid' header in JWT");
      int valueStart = headerJson.indexOf('"', kidStart + 5) + 1;
      int valueEnd = headerJson.indexOf('"', valueStart);
      return headerJson.substring(valueStart, valueEnd);
    } catch (AuthorizationTokenException e) {
      throw e;
    } catch (Exception e) {
      throw new AuthorizationTokenException("Could not read JWT header", e);
    }
  }
}
