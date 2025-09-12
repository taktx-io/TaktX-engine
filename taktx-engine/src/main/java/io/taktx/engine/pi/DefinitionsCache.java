/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class DefinitionsCache {
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> cache = new HashMap<>();

  public ProcessDefinitionDTO computeIfAbsent(
      ProcessDefinitionKey processDefinitionKey,
      Function<ProcessDefinitionKey, ProcessDefinitionDTO> function) {
    log.info("Retrieving process definition {} from cache", processDefinitionKey);
    return cache.computeIfAbsent(processDefinitionKey, key ->{
          log.info("Not found in cache, checking store");
          return function.apply(processDefinitionKey);
        });
  }

  public void put(
      ProcessDefinitionKey processDefinitionKey, ProcessDefinitionDTO processDefinitionDTO) {
    log.info("Adding process definition {} to cache", processDefinitionKey);
    cache.put(processDefinitionKey, processDefinitionDTO);
  }
}
