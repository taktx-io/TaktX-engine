/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.AuthorizationTokenValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Validates the {@code X-TaktX-Authorization} Kafka header on incoming commands.
 *
 * <p>When {@code taktx.security.authorization.enabled=true}: validates the RS256 JWT, matches
 * claims to the command payload, and prevents replay via {@link NonceStore}.
 *
 * <p>Returns the {@code auditId} from the token (or {@code null} when disabled).
 */
@ApplicationScoped
@Slf4j
public class EngineAuthorizationService {

  static final String AUTH_HEADER = "X-TaktX-Authorization";

  private final TaktConfiguration config;
  private final NonceStore nonceStore;
  private final AuthorizationTokenValidator validator;

  @Inject
  public EngineAuthorizationService(
      TaktConfiguration config, PublicKeyProvider publicKeyProvider, NonceStore nonceStore) {
    this.config = config;
    this.nonceStore = nonceStore;
    this.validator =
        new AuthorizationTokenValidator(
            issuer -> {
              if (!publicKeyProvider.isReady()) {
                throw new AuthorizationTokenException("PublicKeyProvider not ready");
              }
              return publicKeyProvider.getPlatformKey();
            });
  }

  /**
   * Authorises an incoming command.
   *
   * @return the {@code auditId} from the token, or {@code null} if authorization is disabled
   * @throws AuthorizationTokenException if validation fails — command must be rejected
   */
  public String authorize(Headers headers, ProcessInstanceTriggerDTO trigger) {
    if (!config.isAuthorizationEnabled()) {
      return null;
    }
    Header authHeader = headers.lastHeader(AUTH_HEADER);
    if (authHeader == null || authHeader.value() == null) {
      throw new AuthorizationTokenException(
          "Missing required "
              + AUTH_HEADER
              + " header on command "
              + trigger.getClass().getSimpleName());
    }
    String rawJwt = new String(authHeader.value(), StandardCharsets.UTF_8);
    TokenClaims claims = validator.validate(rawJwt);
    validateClaimsMatchCommand(claims, trigger);
    if (config.isNonceCheckEnabled() && !nonceStore.checkAndRecord(claims.getAuditId())) {
        throw new AuthorizationTokenException("Replayed auditId detected: " + claims.getAuditId());
      }

    log.info(
        "✅ Authorised command={} user={} auditId={}",
        trigger.getClass().getSimpleName(),
        claims.getUserId(),
        claims.getAuditId());
    return claims.getAuditId();
  }

  private void validateClaimsMatchCommand(TokenClaims claims, ProcessInstanceTriggerDTO trigger) {
    if (trigger instanceof StartCommandDTO start) {
      if (!"START".equals(claims.getAction())) {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match START command");
      }
      String defId =
          start.getProcessDefinitionKey() != null
              ? start.getProcessDefinitionKey().getProcessDefinitionId()
              : null;
      Integer defVersion =
          start.getProcessDefinitionKey() != null
              ? start.getProcessDefinitionKey().getVersion()
              : null;
      if (claims.getProcessDefinitionId() != null
          && !claims.getProcessDefinitionId().equals(defId)) {
        throw new AuthorizationTokenException(
            "Token processDefinitionId '"
                + claims.getProcessDefinitionId()
                + "' does not match command '"
                + defId
                + "'");
      }
      if (claims.getVersion() > 0 && defVersion != null && claims.getVersion() != defVersion) {
        throw new AuthorizationTokenException(
            "Token version "
                + claims.getVersion()
                + " does not match command version "
                + defVersion);
      }
    } else if (trigger instanceof AbortTriggerDTO) {
      if (!"CANCEL".equals(claims.getAction())) {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match CANCEL command");
      }
    } else {
      log.debug("No claim matching defined for {}, allowing", trigger.getClass().getSimpleName());
    }
  }
}
