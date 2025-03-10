package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskTriggerDTOParameterResolver implements TaktParameterResolver {

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return externalTaskTriggerDTO;
  }
}
