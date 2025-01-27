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
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.FlowNodeInstancesVariables;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.SubProcessInstance;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
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
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    subProcessInstance.setFlowNodeInstances(flowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowNodeInstancesVariables flowNodeInstancesVariables =
        flowNodeInstanceVariables.selectFlowNodeInstancesScope(
            subProcessInstance.getElementInstanceId());
    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();

    flowNodeInstancesProcessor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        Constants.NONE,
        subProcessInstance,
        subProcessElements,
        processInstance,
        flowNodeInstancesVariables,
        flowNodeInstances,
        processingStatistics);

    if (flowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTriggerDTO trigger,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    FlowNodeInstancesVariables flowNodeInstancesVariables =
        flowNodeInstanceVariables.selectFlowNodeInstancesScope(
            subProcessInstance.getElementInstanceId());
    flowNodeInstancesProcessor.processContinue(
        flowNodeInstanceStore,
        instanceResult,
        subProcessLevel,
        trigger,
        subProcessElements,
        processInstance,
        flowNodeInstancesVariables,
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
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    FlowNodeInstancesVariables flowNodeInstancesVariables =
        flowNodeInstanceVariables.selectFlowNodeInstancesScope(
            flowNodeInstances.getFlowNodeInstancesId());

    TerminateTriggerDTO trigger =
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of());
    flowNodeInstancesProcessor.processTerminate(
        flowNodeInstanceStore,
        instanceResult,
        trigger,
        processInstance,
        flowNodeInstances,
        flowNodeInstancesVariables,
        subProcessInstance.getFlowNode().getElements(),
        processingStatistics);
  }
}
