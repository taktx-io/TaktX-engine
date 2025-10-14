/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import java.util.Set;

public class BpmnMapperFactory {

  public static final String NS_ZEEBE_1_0 = "http://camunda.org/schema/zeebe/1.0";
  private final Set<String> namespaces;

  BpmnMapperFactory(Set<String> namespaces) {
    this.namespaces = namespaces;
  }

  public BpmnMapper createBpmnMapper() {
    return new GenericBpmnMapper(this);
  }

  public RootElementMapper createRootElementMapper() {
    return new GenericRootElementMapper(this);
  }

  public FlowElementMapper createFlowElementMapper() {
    return new GenericFlowElementMapper(this);
  }

  public EventDefinitionMapper createEventDefinitionMapper() {
    return new GenericEventDefinitionMapper();
  }

  public LoopCharacteristicsMapper createLoopCharacteristicsMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeLoopCharacteristicsMapper();
    } else {
      return new GenericLoopCharacteristicsMapper();
    }
  }

  public CallActivityMapper createCallActivityMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeCallActivityMapper();
    } else {
      return new GenericCallActivityMapper();
    }
  }

  public ServiceTaskMapper createServiceTaskMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeServiceTaskMapper();
    } else {
      return new GenericServiceTaskMapper();
    }
  }

  public SendTaskMapper createSendTaskMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeSendTaskMapper();
    } else {
      return new GenericSendTaskMapper();
    }
  }

  public UserTaskMapper createUserTaskMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeUserTaskMapper();
    } else {
      return new GenericUserTaskMapper();
    }
  }

  public ScriptTaskMapper createScriptTaskMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeScriptTaskMapper();
    } else {
      return new GenericScriptTaskMapper();
    }
  }

  public ReceiveTaskMapper createReceiveTaskMapper() {
    return new GenericReceiveTaskMapper();
  }

  public MessageMapper createMessageMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeMessagekMapper();
    } else {
      return new GenericMessageMapper();
    }
  }

  public IoMappingMapper getIoMappingMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeIoMappingMapper();
    } else {
      return new GenericIoMappingMapper();
    }
  }

  public EscalationMapper createEscalationMapper() {
    return new GenericEscalationMapper();
  }

  public ErrorMapper createErrorMapper() {
    return new GenericErrorMapper();
  }

  public MessageEndEventMapper createMessageEndEventMapper() {
    if (namespaces.contains(NS_ZEEBE_1_0)) {
      return new ZeebeMessageEndEventMapper();
    } else {
      return new GenericMessageEndEventMapper();
    }
  }
}
