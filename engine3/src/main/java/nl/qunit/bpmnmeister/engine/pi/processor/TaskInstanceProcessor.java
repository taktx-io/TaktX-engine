package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.Task2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.TaskInstance;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task2, TaskInstance, ContinueFlowElementTrigger2> {

  @Inject
  public TaskInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      Task2 flowNode2,
      TaskInstance flownodeInstance,
      Variables2 variables) {
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
