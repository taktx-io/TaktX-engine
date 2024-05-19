package nl.qunit.bpmnmeister.pd.xml;

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
}
