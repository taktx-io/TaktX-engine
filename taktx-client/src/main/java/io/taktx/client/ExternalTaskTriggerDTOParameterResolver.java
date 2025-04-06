package io.taktx.client;

import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerDTOParameterResolver implements TaktParameterResolver {

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskTriggerDTO;
  }
}
