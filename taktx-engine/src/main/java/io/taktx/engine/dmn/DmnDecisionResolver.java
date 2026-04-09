/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionKey;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

/**
 * Resolves a DMN decision by its ID from the global DMN definition store. Scans all deployed DMN
 * definitions and returns the decision from the latest version of the definition that contains it.
 */
@ApplicationScoped
@NoArgsConstructor
@Slf4j
public class DmnDecisionResolver {

  private KafkaStreams kafkaStreams;
  private TaktConfiguration taktConfiguration;

  @Inject
  public DmnDecisionResolver(KafkaStreams kafkaStreams, TaktConfiguration taktConfiguration) {
    this.kafkaStreams = kafkaStreams;
    this.taktConfiguration = taktConfiguration;
  }

  /**
   * Returns the latest deployed {@link DmnDecisionDTO} with the given ID, or empty if not found.
   */
  public Optional<DmnDecisionDTO> resolve(String decisionId) {
    try {
      ReadOnlyKeyValueStore<DmnDefinitionKey, ValueAndTimestamp<DmnDefinitionDTO>> store =
          kafkaStreams.store(
              StoreQueryParameters.fromNameAndType(
                  taktConfiguration.getPrefixed(Stores.GLOBAL_DMN_DEFINITION.getStorename()),
                  QueryableStoreTypes.timestampedKeyValueStore()));

      DmnDefinitionDTO latestDefinition = null;
      DmnDecisionDTO foundDecision = null;

      try (var iterator = store.all()) {
        while (iterator.hasNext()) {
          var entry = iterator.next();
          DmnDefinitionDTO dto = entry.value.value();
          if (dto.getDefinitions() == null || dto.getDefinitions().getDecisions() == null) {
            continue;
          }
          for (DmnDecisionDTO decision : dto.getDefinitions().getDecisions()) {
            if (decisionId.equals(decision.getId())) {
              if (latestDefinition == null
                  || entry.key.getVersion() > latestDefinition.getVersion()) {
                latestDefinition = dto;
                foundDecision = decision;
              }
            }
          }
        }
      }
      return Optional.ofNullable(foundDecision);
    } catch (InvalidStateStoreException e) {
      log.warn("DMN definition store not available when resolving decision '{}'", decisionId, e);
      return Optional.empty();
    }
  }
}
