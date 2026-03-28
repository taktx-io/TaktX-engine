/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.engine.generic.RestObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {

  @Inject @RestObjectMapper ObjectMapper restObjectMapper;

  @Override
  public ObjectMapper getContext(Class<?> type) {
    return restObjectMapper;
  }
}
