package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends GatewayProcessor<ParallelGateway, ParallelGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ParallelGateway element,
      ParallelGatewayState oldState,
      ScopedVars variables) {

    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.getInputFlowId());

    final Set<String> outputFlows = new HashSet<>();
    FlowNodeStateEnum newState = FlowNodeStateEnum.ACTIVE;
    if (element.getIncoming().equals(newTriggeredFlows)) {
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
      newState = FlowNodeStateEnum.FINISHED;
    }
    List<ProcessInstanceTrigger> triggers =
        outputFlows.stream()
            .map(
                flowId -> {
                  SequenceFlow flow =
                      (SequenceFlow)
                          definition
                              .getDefinitions()
                              .getRootProcess()
                              .getFlowElements()
                              .getFlowElement(flowId)
                              .orElseThrow();
                  return new FlowElementTrigger(
                      processInstance.getProcessInstanceKey(),
                      flow.getTarget(),
                      flow.getId(),
                      variables.getCurrentScopeVariables());
                })
            .collect(Collectors.toList());

    return TriggerResult.builder()
        .newFlowNodeState(
            new ParallelGatewayState(
                UUID.randomUUID(),
                newTriggeredFlows,
                oldState.getPassedCnt() + 1,
                newState,
                oldState.getInputFlowId()))
        .processInstanceTriggers(triggers)
        .build();
  }

  @Override
  protected ParallelGatewayState getTerminateElementState(ParallelGatewayState elementState) {
    return new ParallelGatewayState(
        elementState.getElementInstanceId(),
        elementState.getTriggeredFlows(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
