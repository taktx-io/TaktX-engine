package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.TaskInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task, TaskInstance, ContinueFlowElementTrigger> {

  @Inject
  public TaskInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(TaskInstance instance) {
    // Nothing to do here
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements flowElements,
      TaskInstance flowNodeInstance,
      String inputFlowId,
      Variables variables) {
    flowNodeInstance.setState(ActtivityStateEnum.FINISHED);
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements flowElements,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables) {
    return InstanceResult.empty();
  }
}
