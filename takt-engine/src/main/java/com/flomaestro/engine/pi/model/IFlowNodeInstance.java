package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import java.util.List;

public interface IFlowNodeInstance {

  long getElementInstanceId();

  FlowNodeInstance<?> getParentInstance();

  FlowNode getFlowNode();

  List<Long> getKeyPath();
}
