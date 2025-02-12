package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SubProcess;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.FlowNodeInstancesProcessor;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.SubProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Queue;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess, SubProcessInstance, ContinueFlowElementTriggerDTO> {

  private FlowNodeInstancesProcessor flowNodeInstancesProcessor;

  @Inject
  public SubProcessInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstances subFlowNodeInstances = new FlowNodeInstances();
    subFlowNodeInstances.setParentFlowNodeInstance(subProcessInstance);
    subProcessInstance.setFlowNodeInstances(subFlowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(flowElements.getIndex());

    flowNodeInstancesProcessor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        null,
        subProcessInstance,
        subProcessElements,
        processInstance,
        flowNodeInstanceVariables,
        subFlowNodeInstances,
        processingStatistics);

    if (subFlowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
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
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    subProcessInstance.getFlowNodeInstances().setParentFlowNodeInstance(subProcessInstance);

    flowNodeInstancesProcessor.processContinue(
        flowNodeInstanceStore,
        instanceResult,
        subProcessLevel,
        trigger,
        subProcessElements,
        processInstance,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNodeInstances(),
        processingStatistics);

    Queue<EventSignal> bubbleUpEvents = instanceResult.getBubbleUpEvents();
    EventSignal eventSignal = bubbleUpEvents.poll();
    while (eventSignal != null) {
      eventSignal.bubbleUp();
      directInstanceResult.addEvent(eventSignal);
      eventSignal = bubbleUpEvents.poll();
    }

    if (subProcessInstance.getFlowNodeInstances().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setParentFlowNodeInstance(subProcessInstance);

    TerminateTriggerDTO trigger =
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of());
    flowNodeInstancesProcessor.processTerminate(
        flowNodeInstanceStore,
        instanceResult,
        trigger,
        processInstance,
        flowNodeInstances,
        flowNodeInstanceVariables,
        subProcessInstance.getFlowNode().getElements(),
        processingStatistics);
  }
}
