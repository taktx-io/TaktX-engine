package io.taktx.client;

import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;

public interface TaktParameterResolver {

  Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO);
}
