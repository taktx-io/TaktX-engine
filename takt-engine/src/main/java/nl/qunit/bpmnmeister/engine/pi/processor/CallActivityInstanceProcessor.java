package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.CallActivity;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.NewStartCommand;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.CallActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity, CallActivityInstance, ContinueFlowElementTrigger> {

  @Inject
  public CallActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      CallActivityInstance callActivityInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables) {
    callActivityInstance.setState(ActtivityStateEnum.WAITING);

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
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      CallActivityInstance instance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables) {
    instance.setState(ActtivityStateEnum.FINISHED);
    if (instance.getFlowNode().isPropagateAllChildVariables()) {
      processInstanceVariables.merge(variablesMapper.fromDTO(trigger.getVariables()));
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      CallActivityInstance instance,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {
    instanceResult.addTerminateCommand(instance.getChildProcessInstanceId());
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
