package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.client.annotation.Variable;
import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;
import java.lang.reflect.Parameter;
import java.util.Map;

public class DefaultTaktParameterResolverFactory implements TaktParameterResolverFactory {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private final ExternalTaskResponder externalTaskResponder;

  public DefaultTaktParameterResolverFactory(ExternalTaskResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public TaktParameterResolver create(TaktClient taktClient, Parameter parameter) {
    if (parameter.getType().isAssignableFrom(ExternalTaskTriggerDTO.class)) {
      return new ExternalTaskTriggerDTOParameterResolver();
    } else if (parameter.getType().isAssignableFrom(ExternalTaskInstanceResponder.class)) {
      return new ExternalTaskInstanceResponderParameterResolver(externalTaskResponder);
    } else if (parameter.getType().isAssignableFrom(TaktClient.class)) {
      return new TaktClientParameterResolver(taktClient);
    } else if (parameter.getAnnotation(Variable.class) != null) {
      return new VariableParameterResolver(
          OBJECT_MAPPER, parameter.getType(), parameter.getAnnotation(Variable.class).value());
    } else if (parameter.getType().isAssignableFrom(Map.class)) {
      return new MapParameterResolver(OBJECT_MAPPER);
    } else {
      return new VariableParameterResolver(OBJECT_MAPPER, parameter.getType(), parameter.getName());
    }
  }
}
