package nl.qunit.bpmnmeister.engine.generic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;
import nl.qunit.bpmnmeister.pd.model.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTriggerDTO;
import nl.qunit.bpmnmeister.pi.InstanceUpdateDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTriggerDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.MessageEventDTO;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

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
            .allowIfBaseType(SchedulableMessage.class)
            .allowIfBaseType(MessageScheduler.class)
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
