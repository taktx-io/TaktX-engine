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
