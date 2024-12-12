package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import java.util.UUID;

public interface IFlowNodeInstance {

  UUID getElementInstanceId();

  FlowNodeInstance<?> getParentInstance();

  FlowNode getFlowNode();
}
