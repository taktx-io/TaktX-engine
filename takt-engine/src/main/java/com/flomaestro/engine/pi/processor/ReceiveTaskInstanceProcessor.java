package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.ReceiveTask;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ReceiveTaskInstance;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class ReceiveTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        ReceiveTask, ReceiveTaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public ReceiveTaskInstanceProcessor(
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
      ReceiveTaskInstance receiveTaskInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    receiveTaskInstance.setState(ActtivityStateEnum.WAITING);

    ReceiveTask receiveTask = receiveTaskInstance.getFlowNode();
    String correlationKeyExpression = receiveTask.getReferencedMessage().correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName = receiveTask.getReferencedMessage().name();
    receiveTaskInstance.setCorrelationKey(correlationKey);
    instanceResult.addNewCorrelationSubcriptionMessageEvent(
        new NewCorrelationSubscriptionMessageEventInfo(
            messageName, correlationKey, receiveTaskInstance));
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    receiveTaskInstance.setState(ActtivityStateEnum.FINISHED);
    terminatingSubscriptionInstanceResult(instanceResult, receiveTaskInstance);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ReceiveTaskInstance instance,
      ProcessInstance processInstance,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    terminatingSubscriptionInstanceResult(instanceResult, instance);
  }

  private static void terminatingSubscriptionInstanceResult(
      InstanceResult instanceResult, ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTaskInstance.getFlowNode().getReferencedMessage().name();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
  }
}
