/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class FrontendObjectMapperConfig {

  @Produces
  @Named("frontendObjectMapper")
  @ApplicationScoped
  public ObjectMapper createFrontendObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Custom annotation introspector that ignores JsonFormat ARRAY shape
    mapper.setAnnotationIntrospector(
        new JacksonAnnotationIntrospector() {
          @Override
          public JsonFormat.Value findFormat(Annotated memberOrClass) {
            JsonFormat.Value format = super.findFormat(memberOrClass);
            if (format != null && format.getShape() == JsonFormat.Shape.ARRAY) {
              // Return a format that uses OBJECT shape instead of ARRAY
              return JsonFormat.Value.forShape(JsonFormat.Shape.OBJECT);
            }
            return format;
          }
        });

    return mapper;
  }
}
