package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.CallActivity2;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.CallActivityInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity2, CallActivityInstance, ContinueFlowElementTrigger2> {

  private VariablesMapper variablesMapper;

  @Inject
  public CallActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor);
    this.variablesMapper = variablesMapper;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, CallActivityInstance callActivityInstance, Variables2 variables) {
    callActivityInstance.setState(FlowNodeStateEnum.WAITING);

    InstanceResult instanceResult = InstanceResult.empty();
    UUID newProcessInstanceKey = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceKey);
    CallActivity2 flowNode = callActivityInstance.getFlowNode();
    instanceResult.addNewStartCommand(
        new NewStartCommand(
            newProcessInstanceKey,
            Constants.NONE_UUID,
            flowNode,
            callActivityInstance,
            flowNode.getCalledElement(),
            variables));
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      //      CallActivity2 callActivity,
      CallActivityInstance instance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    instance.setState(FlowNodeStateEnum.FINISHED);
    if (instance.getFlowNode().isPropagateAllChildVariables()) {
      processInstanceVariables.merge(variablesMapper.fromDTO(trigger.getVariables()));
    }
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(
      CallActivity2 flowNode, CallActivityInstance instance) {
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addTerminateCommand(instance.getChildProcessInstanceId());
    return instanceResult;
  }

  @Override
  protected Variables2 getInputVariables(
      CallActivity2 callActivity, Variables2 processInstanceVariables) {
    Variables2 inputVariables = Variables2.empty();
    if (callActivity.isPropagateAllParentVariables()) {
      inputVariables.merge(processInstanceVariables);
    }
    inputVariables.merge(
        ioMappingProcessor.getInputVariables(callActivity, processInstanceVariables));
    return inputVariables;
  }
}
