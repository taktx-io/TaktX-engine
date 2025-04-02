/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.engine.generic.RestObjectMapper;
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
