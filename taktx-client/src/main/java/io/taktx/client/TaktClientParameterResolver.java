package io.taktx.client;

import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;

public class TaktClientParameterResolver implements TaktParameterResolver {

  private final TaktClient taktClient;

  public TaktClientParameterResolver(TaktClient taktClient) {
    this.taktClient = taktClient;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return taktClient;
  }
}
