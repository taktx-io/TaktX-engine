/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

  @Singleton
  @Produces
  @RestObjectMapper
  public ObjectMapper restObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Add any other custom configurations here
    return objectMapper;
  }
}
