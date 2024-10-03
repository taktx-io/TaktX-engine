package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.CallActivityInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity, CallActivityInstance, ContinueFlowElementTrigger> {

  @Inject
  public CallActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements flowElements,
      CallActivityInstance callActivityInstance,
      String inputFlowId,
      Variables variables) {
    callActivityInstance.setState(ActtivityStateEnum.WAITING);

    InstanceResult instanceResult = InstanceResult.empty();
    UUID newProcessInstanceKey = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceKey);
    CallActivity flowNode = callActivityInstance.getFlowNode();
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
      FlowElements flowElements,
      //      CallActivity2 callActivity,
      CallActivityInstance instance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables) {
    instance.setState(ActtivityStateEnum.FINISHED);
    if (instance.getFlowNode().isPropagateAllChildVariables()) {
      processInstanceVariables.merge(variablesMapper.fromDTO(trigger.getVariables()));
    }
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(CallActivityInstance instance) {
    InstanceResult instanceResult = InstanceResult.empty();
    instanceResult.addTerminateCommand(instance.getChildProcessInstanceId());
    return instanceResult;
  }

  @Override
  protected Variables getInputVariables(
      CallActivity callActivity, Variables processInstanceVariables) {
    Variables inputVariables = Variables.empty();
    if (callActivity.isPropagateAllParentVariables()) {
      inputVariables.merge(processInstanceVariables);
    }
    inputVariables.merge(
        ioMappingProcessor.getInputVariables(callActivity, processInstanceVariables));
    return inputVariables;
  }
}
