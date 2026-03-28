/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class DefinitionsCache {
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> cache = new ConcurrentHashMap<>();

  public ProcessDefinitionDTO computeIfAbsent(
      ProcessDefinitionKey processDefinitionKey,
      Function<ProcessDefinitionKey, ProcessDefinitionDTO> function) {
    log.debug("Retrieving process definition {} from cache", processDefinitionKey);

    // First, try to get from cache without any locking
    ProcessDefinitionDTO cached = cache.get(processDefinitionKey);
    if (cached != null) {
      return cached;
    }

    // If not in cache, synchronize on the key to avoid duplicate work
    synchronized (this) {
      // Double-check after acquiring lock
      cached = cache.get(processDefinitionKey);
      if (cached != null) {
        return cached;
      }

      // Not in cache, load it
      log.info("Not found in cache, checking store");
      ProcessDefinitionDTO result = function.apply(processDefinitionKey);
      if (result != null) {
        cache.put(processDefinitionKey, result);
      }
      return result;
    }
  }

  public void put(
      ProcessDefinitionKey processDefinitionKey, ProcessDefinitionDTO processDefinitionDTO) {
    log.info("Adding process definition {} to cache", processDefinitionKey);
    cache.put(processDefinitionKey, processDefinitionDTO);
  }
}
