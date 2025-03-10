package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskInstanceResponderParameterResolver implements TaktParameterResolver {

  private final ExternalTaskResponder externalTaskResponder;

  public ExternalTaskInstanceResponderParameterResolver(ExternalTaskResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }
}
