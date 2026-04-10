/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class DmnDefinitionsCache {
  private final Map<DmnDefinitionKey, DmnDefinitionDTO> cache = new ConcurrentHashMap<>();

  public DmnDefinitionDTO computeIfAbsent(
      DmnDefinitionKey key, Function<DmnDefinitionKey, DmnDefinitionDTO> loader) {
    log.debug("Retrieving DMN definition {} from cache", key);
    DmnDefinitionDTO cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
      log.info("DMN definition {} not in cache, loading from store", key);
      DmnDefinitionDTO result = loader.apply(key);
      if (result != null) {
        cache.put(key, result);
      }
      return result;
    }
  }

  public void put(DmnDefinitionKey key, DmnDefinitionDTO dto) {
    log.info("Adding DMN definition {} to cache", key);
    cache.put(key, dto);
  }
}
