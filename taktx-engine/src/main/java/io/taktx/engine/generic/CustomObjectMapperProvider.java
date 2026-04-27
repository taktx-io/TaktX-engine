/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.generic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.taktx.dto.BaseElementDTO;
import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SchedulableMessageDTO;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;

public class CustomObjectMapperProvider {

  // Replaces the CDI producer for ObjectMapper built into Quarkus
  @Singleton
  @Produces
  public ObjectMapper objectMapper(@All List<ObjectMapperCustomizer> customizers) {

    var mapper = new MyObjectMapper();
    // Apply all ObjectMapperCustomizer beans (incl. Quarkus)
    for (ObjectMapperCustomizer customizer : customizers) {
      customizer.customize(mapper);
    }

    PolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(DefinitionsTriggerDTO.class)
            .allowIfBaseType(BaseElementDTO.class)
            .allowIfBaseType(SchedulableMessageDTO.class)
            .allowIfBaseType(MessageScheduleDTO.class)
            .allowIfBaseType(ProcessInstanceTriggerDTO.class)
            .allowIfBaseType(FlowNodeInstanceDTO.class)
            .allowIfBaseType(MessageEventDTO.class)
            .allowIfBaseType(InstanceUpdateDTO.class)
            .build();

    mapper.setPolymorphicTypeValidator(ptv);
    // Configure the ObjectMapper to ignore unknown properties
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return mapper;
  }

  private class MyObjectMapper extends ObjectMapper {

    public MyObjectMapper() {
      super(new CBORFactory());
    }
  }
}
