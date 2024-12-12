package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.SubProcess;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class SubProcessInstance extends ActivityInstance<SubProcess>
    implements WithFlowNodeInstances {

  private FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

  public SubProcessInstance(FlowNodeInstance<?> parentInstance, SubProcess flowNode) {
    super(parentInstance, flowNode);
  }
}
