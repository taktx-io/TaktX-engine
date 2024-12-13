package com.flomaestro.engine.generic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.BaseElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
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
            .allowIfBaseType(MessageSchedulerDTO.class)
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
