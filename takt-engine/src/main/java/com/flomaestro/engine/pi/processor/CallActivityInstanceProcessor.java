package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.CallActivity;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.CallActivityInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity, CallActivityInstance, ContinueFlowElementTriggerDTO> {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  public CallActivityInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
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

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(flowNode.getCalledElement(), variables);

    instanceResult.addNewStartCommand(
        new NewStartCommand(
            newProcessInstanceKey,
            Constants.NONE_UUID,
            flowNode,
            callActivityInstance,
            jsonNode.asText(),
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
      ContinueFlowElementTriggerDTO trigger,
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
