package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.Gateway;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceException;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.GatewayInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowConditionDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@NoArgsConstructor
public abstract class GatewayInstanceProcessor<
        E extends Gateway, I extends GatewayInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FlowNodeInstanceProcessor<E, I, C> {

  private FeelExpressionHandler feelExpressionHandler;

  protected GatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I gatewayInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    processStartSpecificGatewayInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        gatewayInstance,
        inputFlowId,
        variables,
        processingStatistics);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      C trigger,
      VariableScope processInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    // Should never happen
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I gatewayInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    Set<SequenceFlow> outgoingFlows = new HashSet<>();
    if (canTriggerOutputFlows(gatewayInstance, flowNodeInstances)) {
      gatewayInstance.resetFlows();
      E gatewayNode = gatewayInstance.getFlowNode();
      Set<SequenceFlow> sequenceFlows = gatewayNode.getOutGoingSequenceFlows();
      Set<SequenceFlow> flowsWithCondition =
          sequenceFlows.stream()
              .filter(sequenceFlow -> !FlowConditionDTO.NONE.equals(sequenceFlow.getCondition()))
              .filter(
                  sequenceFlow ->
                      feelExpressionHandler
                          .processFeelExpression(
                              sequenceFlow.getCondition().getExpression(), variables)
                          .asBoolean())
              .collect(Collectors.toSet());

      outgoingFlows.addAll(flowsWithCondition);
      if (outgoingFlows.isEmpty() && gatewayNode.getDefaultFlow() != null) {
        outgoingFlows.add(gatewayNode.getDefaultSequenceFlow());
      } else if (outgoingFlows.isEmpty() && !sequenceFlows.isEmpty()) {
        // Last chance, if no condition is met and no default flow is set, take the outgoing flows
        outgoingFlows.addAll(sequenceFlows);
      }
      if (outgoingFlows.isEmpty()) {
        throw new ProcessInstanceException(
            processInstance,
            gatewayInstance,
            "No outgoing flow could be selected found for exclusive gateway: "
                + gatewayNode.getId());
      }
    }
    gatewayInstance.setSelectedOutputFlows(
        outgoingFlows.stream().map(SequenceFlow::getId).collect(Collectors.toSet()));
    return outgoingFlows;
  }

  protected abstract boolean canTriggerOutputFlows(
      I gatewayInstance, FlowNodeInstances flowNodeInstances);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      VariableScope currentVariableScope,
      ProcessingStatistics processingStatistics,
      FlowElements flowElements) {
    processTerminateSpecificGatewayInstance(instanceResult, directInstanceResult, instance);
  }

  protected abstract void processStartSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics);

  protected abstract void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult, DirectInstanceResult directInstanceResult, I instance);
}
