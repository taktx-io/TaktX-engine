package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.CallActivity;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.CallActivityInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity, CallActivityInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public CallActivityInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      CallActivityInstance callActivityInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    callActivityInstance.setState(ActtivityStateEnum.WAITING);

    UUID newProcessInstanceKey = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceKey);
    CallActivity flowNode = callActivityInstance.getFlowNode();

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(flowNode.getCalledElement(), variables);
    if (jsonNode != null) {
      VariablesDTO commandVariables;
      if (callActivityInstance.getFlowNode().isPropagateAllParentVariables()) {
        commandVariables = variables.scopeAndParentsToDto();
      } else {
        commandVariables = variables.scopeToDTO();
      }

      instanceResult.addNewStartCommand(
          new NewStartCommand(
              newProcessInstanceKey,
              flowNode,
              callActivityInstance,
              jsonNode.asText(),
              commandVariables,
              callActivityInstance.getFlowNode().isPropagateAllChildVariables(),
              callActivityInstance.getFlowNode().getIoMapping().getOutputMappings()));
    } else {
      callActivityInstance.setState(ActtivityStateEnum.FAILED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      CallActivityInstance instance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    instance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      CallActivityInstance instance,
      ProcessInstance processInstance,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    instanceResult.addTerminateCommand(instance.getChildProcessInstanceId());
  }
}
