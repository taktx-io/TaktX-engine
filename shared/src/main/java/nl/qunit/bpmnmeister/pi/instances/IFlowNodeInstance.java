package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;

public interface IFlowNodeInstance {

  UUID getElementInstanceId();

  FLowNodeInstance<?> getParentInstance();

  FlowNode2 getFlowNode();
}
