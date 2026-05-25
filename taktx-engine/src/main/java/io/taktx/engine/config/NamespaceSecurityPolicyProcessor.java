/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.NamespaceSecurityPolicyActivationService;
import io.taktx.proto.NamespaceSecurityPolicyMessage;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.NamespaceSecurityPolicySupport;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global-store processor for the namespace-local {@code taktx-security-policy} topic.
 *
 * <p>The first slice intentionally keeps this processor narrow: it validates and caches the latest
 * explicit policy record so downstream runtime components can adopt the official contract without
 * yet implementing activation-state transitions here.
 */
public class NamespaceSecurityPolicyProcessor implements Processor<String, byte[], Void, Void> {

  private static final Logger log = LoggerFactory.getLogger(NamespaceSecurityPolicyProcessor.class);

  static final String POLICY_KEY = "policy";

  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  private final NamespaceSecurityPolicyActivationService activationService;
  private final EngineAuthorizationService engineAuthorizationService;

  public NamespaceSecurityPolicyProcessor(
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore) {
    this(namespaceSecurityPolicyStore, null, null);
  }

  public NamespaceSecurityPolicyProcessor(
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      NamespaceSecurityPolicyActivationService activationService) {
    this(namespaceSecurityPolicyStore, activationService, null);
  }

  public NamespaceSecurityPolicyProcessor(
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      NamespaceSecurityPolicyActivationService activationService,
      EngineAuthorizationService engineAuthorizationService) {
    this.namespaceSecurityPolicyStore = namespaceSecurityPolicyStore;
    this.activationService = activationService;
    this.engineAuthorizationService = engineAuthorizationService;
  }

  @Override
  public void init(ProcessorContext<Void, Void> context) {
    // nothing to initialize
  }

  @Override
  public void process(Record<String, byte[]> rec) {
    if (!POLICY_KEY.equals(rec.key())) {
      log.debug(
          "Ignoring record for non-policy key='{}' on taktx-security-policy topic", rec.key());
      return;
    }

    try {
      if (engineAuthorizationService != null) {
        engineAuthorizationService.authorizeNamespaceSecurityPolicyMutation(
            rec.headers(), rec.value());
      }

      if (rec.value() == null) {
        if (activationService != null) {
          activationService.onPolicyCleared();
        } else {
          namespaceSecurityPolicyStore.clear();
        }
        log.info("Namespace security policy cleared from tombstone record");
        return;
      }

      NamespaceSecurityPolicyDTO policy =
          NamespaceSecurityPolicyProtoMapper.toDto(
              NamespaceSecurityPolicyMessage.parseFrom(rec.value()));
      NamespaceSecurityPolicyDTO validated = NamespaceSecurityPolicySupport.requireValid(policy);
      if (activationService != null) {
        activationService.onPolicyUpdated(validated);
      } else {
        namespaceSecurityPolicyStore.update(validated);
      }
      log.info(
          "Namespace security policy updated: activationState={} desiredPolicyVersion={} desiredPolicyHash={} activePolicyVersion={} activePolicyHash={} mode={}",
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getActivationState()
              : null,
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getDesiredPolicyVersion()
              : validated.getDesiredPolicyVersion(),
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getDesiredPolicyHash()
              : validated.getDesiredPolicyHash(),
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getActivePolicyVersion()
              : validated.getActivePolicyVersion(),
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getActivePolicyHash()
              : validated.getActivePolicyHash(),
          namespaceSecurityPolicyStore.get() != null
              ? namespaceSecurityPolicyStore.get().getMode()
              : validated.getMode());
    } catch (AuthorizationTokenException e) {
      if (activationService != null) {
        activationService.onRejectedPolicyMutation(e.getMessage(), rec.key());
      }
      log.warn("Rejected unauthorized namespace security policy mutation: {}", e.getMessage());
    } catch (Exception e) {
      if (activationService != null) {
        activationService.onRejectedPolicyMutation(e.getMessage(), rec.key());
      }
      log.warn("Failed to deserialize or validate namespace security policy: {}", e.getMessage());
    }
  }

  @Override
  public void close() {
    // nothing to close
  }
}
