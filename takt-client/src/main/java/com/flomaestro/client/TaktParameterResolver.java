package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;

public interface TaktParameterResolver {

  Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO);
}
