package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.Task2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.TaskInstance;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task2, TaskInstance, ContinueFlowElementTrigger2> {

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, Task2 flowNode2, TaskInstance flownodeInstance) {
    flownodeInstance.setState(FlowNodeStateEnum.FINISHED);
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      FlowElements2 flowElements,
      Task2 externalTask,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables) {
    return InstanceResult.empty();
  }
}
