package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.model.FlowNode;

public interface IFlowNodeInstance {

  UUID getElementInstanceId();

  FLowNodeInstance<?> getParentInstance();

  FlowNode getFlowNode();
}
