package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerDTOParameterResolver implements TaktParameterResolver {

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskTriggerDTO;
  }
}
