package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;

public interface TaktParameterResolver {

  Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO);
}
