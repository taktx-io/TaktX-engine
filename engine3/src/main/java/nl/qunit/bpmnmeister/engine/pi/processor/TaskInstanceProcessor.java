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
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task2, TaskInstance, ContinueFlowElementTrigger2> {

  @Inject
  public TaskInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(TaskInstance instance) {
    // Nothing to do here
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      TaskInstance flowNodeInstance,
      String inputFlowId,
      Variables2 variables) {
    flowNodeInstance.setState(ActtivityStateEnum.FINISHED);
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    return InstanceResult.empty();
  }
}
