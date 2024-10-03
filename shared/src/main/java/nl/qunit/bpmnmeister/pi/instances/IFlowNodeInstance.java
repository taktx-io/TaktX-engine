package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.FlowNode;

public interface IFlowNodeInstance {

  UUID getElementInstanceId();

  FLowNodeInstance<?> getParentInstance();

  FlowNode getFlowNode();
}
