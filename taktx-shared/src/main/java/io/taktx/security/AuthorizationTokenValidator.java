/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
 * that resolves a key by the JWT {@code iss} claim. Works in both Quarkus (engine) and Spring
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

    // Peek at the issuer before signature verification to resolve the correct key
    String issuer = peekIssuer(rawJwt);
    PublicKey publicKey = keySource.getKey(issuer);

    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(publicKey)
              .build()
              .parseSignedClaims(rawJwt)
              .getPayload();

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

  /** Extracts the {@code iss} claim without verifying the signature. */
  private static String peekIssuer(String rawJwt) {
    try {
      String[] parts = rawJwt.split("\\.");
      if (parts.length < 2) throw new AuthorizationTokenException("Malformed JWT");
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
      int issStart = payloadJson.indexOf("\"iss\"");
      if (issStart < 0) throw new AuthorizationTokenException("Missing 'iss' claim in JWT");
      int valueStart = payloadJson.indexOf('"', issStart + 5) + 1;
      int valueEnd = payloadJson.indexOf('"', valueStart);
      return payloadJson.substring(valueStart, valueEnd);
    } catch (AuthorizationTokenException e) {
      throw e;
    } catch (Exception e) {
      throw new AuthorizationTokenException("Could not read JWT payload", e);
    }
  }
}
