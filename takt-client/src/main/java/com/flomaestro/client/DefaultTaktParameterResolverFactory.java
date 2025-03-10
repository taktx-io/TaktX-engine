package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import java.lang.reflect.Parameter;

public class DefaultTaktParameterResolverFactory implements TaktParameterResolverFactory {

  private final ExternalTaskResponder externalTaskResponder;

  public DefaultTaktParameterResolverFactory(ExternalTaskResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public TaktParameterResolver create(Parameter parameter) {
    if (parameter.getType().isAssignableFrom(ExternalTaskTriggerDTO.class)) {
      return new ExternalTaskTriggerDTOParameterResolver();
    } else if (parameter.getType().isAssignableFrom(ExternalTaskInstanceResponder.class)) {
      return new ExternalTaskInstanceResponderParameterResolver(externalTaskResponder);
    }
    return null;
  }
}
