package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

public class ExternalTaskInstanceResponderParameterResolver implements TaktParameterResolver {

  private final ExternalTaskResponder externalTaskResponder;

  public ExternalTaskInstanceResponderParameterResolver(
      ExternalTaskResponder externalTaskResponder) {
    this.externalTaskResponder = externalTaskResponder;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }
}
