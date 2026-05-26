/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BpmnMapperFactoryTest {

  @Test
  void genericNamespaces_createGenericMappers() {
    BpmnMapperFactory factory = new BpmnMapperFactory(Set.of("https://example.com/bpmn"));

    assertThat(factory.createBpmnMapper()).isInstanceOf(GenericBpmnMapper.class);
    assertThat(factory.createRootElementMapper()).isInstanceOf(GenericRootElementMapper.class);
    assertThat(factory.createFlowElementMapper()).isInstanceOf(GenericFlowElementMapper.class);
    assertThat(factory.createEventDefinitionMapper())
        .isInstanceOf(GenericEventDefinitionMapper.class);
    assertThat(factory.createLoopCharacteristicsMapper())
        .isInstanceOf(GenericLoopCharacteristicsMapper.class);
    assertThat(factory.createCallActivityMapper()).isInstanceOf(GenericCallActivityMapper.class);
    assertThat(factory.createServiceTaskMapper()).isInstanceOf(GenericServiceTaskMapper.class);
    assertThat(factory.createSendTaskMapper()).isInstanceOf(GenericSendTaskMapper.class);
    assertThat(factory.createUserTaskMapper()).isInstanceOf(GenericUserTaskMapper.class);
    assertThat(factory.createScriptTaskMapper()).isInstanceOf(GenericScriptTaskMapper.class);
    assertThat(factory.createReceiveTaskMapper()).isInstanceOf(GenericReceiveTaskMapper.class);
    assertThat(factory.createMessageMapper()).isInstanceOf(GenericMessageMapper.class);
    assertThat(factory.getIoMappingMapper()).isInstanceOf(GenericIoMappingMapper.class);
    assertThat(factory.createEscalationMapper()).isInstanceOf(GenericEscalationMapper.class);
    assertThat(factory.createErrorMapper()).isInstanceOf(GenericErrorMapper.class);
    assertThat(factory.createMessageEndEventMapper())
        .isInstanceOf(GenericMessageEndEventMapper.class);
    assertThat(factory.createMessageIntermediateThrowEventMapper())
        .isInstanceOf(GenericMessageIntermediateThrowEventMapper.class);
    assertThat(factory.createSignalMapper()).isInstanceOf(GenericSignalMapper.class);
    assertThat(factory.createBusinessRuleTaskMapper())
        .isInstanceOf(GenericBusinessRuleTaskMapper.class);
  }

  @Test
  void zeebeNamespace_createZeebeAwareMappers() {
    BpmnMapperFactory factory = new BpmnMapperFactory(Set.of(BpmnMapperFactory.NS_ZEEBE_1_0));

    assertThat(factory.createBpmnMapper()).isInstanceOf(GenericBpmnMapper.class);
    assertThat(factory.createRootElementMapper()).isInstanceOf(ZeebeRootElementMapper.class);
    assertThat(factory.createFlowElementMapper()).isInstanceOf(GenericFlowElementMapper.class);
    assertThat(factory.createEventDefinitionMapper())
        .isInstanceOf(GenericEventDefinitionMapper.class);
    assertThat(factory.createLoopCharacteristicsMapper())
        .isInstanceOf(ZeebeLoopCharacteristicsMapper.class);
    assertThat(factory.createCallActivityMapper()).isInstanceOf(ZeebeCallActivityMapper.class);
    assertThat(factory.createServiceTaskMapper()).isInstanceOf(ZeebeServiceTaskMapper.class);
    assertThat(factory.createSendTaskMapper()).isInstanceOf(ZeebeSendTaskMapper.class);
    assertThat(factory.createUserTaskMapper()).isInstanceOf(ZeebeUserTaskMapper.class);
    assertThat(factory.createScriptTaskMapper()).isInstanceOf(ZeebeScriptTaskMapper.class);
    assertThat(factory.createReceiveTaskMapper()).isInstanceOf(GenericReceiveTaskMapper.class);
    assertThat(factory.createMessageMapper()).isInstanceOf(ZeebeMessagekMapper.class);
    assertThat(factory.getIoMappingMapper()).isInstanceOf(ZeebeIoMappingMapper.class);
    assertThat(factory.createEscalationMapper()).isInstanceOf(GenericEscalationMapper.class);
    assertThat(factory.createErrorMapper()).isInstanceOf(GenericErrorMapper.class);
    assertThat(factory.createMessageEndEventMapper())
        .isInstanceOf(ZeebeMessageEndEventMapper.class);
    assertThat(factory.createMessageIntermediateThrowEventMapper())
        .isInstanceOf(ZeebeMessageIntermediateThrowEventMapper.class);
    assertThat(factory.createSignalMapper()).isInstanceOf(GenericSignalMapper.class);
    assertThat(factory.createBusinessRuleTaskMapper())
        .isInstanceOf(ZeebeBusinessRuleTaskMapper.class);
  }
}
