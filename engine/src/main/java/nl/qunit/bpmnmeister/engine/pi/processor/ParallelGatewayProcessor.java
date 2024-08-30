package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.SequenceFlowDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends GatewayProcessor<ParallelGatewayDTO, ParallelGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      ParallelGatewayDTO element,
      ParallelGatewayState oldState,
      ScopedVars variables) {

    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.getInputFlowId());

    final Set<String> outputFlows = new HashSet<>();
    FlowNodeStateEnum newState = FlowNodeStateEnum.WAITING;
    if (element.getIncoming().equals(newTriggeredFlows)) {
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
      newState = FlowNodeStateEnum.FINISHED;
    }
    List<ProcessInstanceTrigger> triggers =
        outputFlows.stream()
            .map(
                flowId -> {
                  SequenceFlowDTO flow =
                      (SequenceFlowDTO)
                          definition
                              .getDefinitions()
                              .getRootProcess()
                              .getFlowElements()
                              .getFlowElement(flowId)
                              .orElseThrow();
                  return new StartFlowElementTrigger(
                      processInstance.getProcessInstanceKey(),
                      oldState.getElementInstanceId(),
                      flow.getTarget(),
                      flow.getId(),
                      variables.getCurrentScopeVariables());
                })
            .collect(Collectors.toList());

    return TriggerResult.builder()
        .newFlowNodeStates(
            List.of(
                new ParallelGatewayState(
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    newTriggeredFlows,
                    oldState.getPassedCnt() + 1,
                    newState,
                    oldState.getInputFlowId())))
        .processInstanceTriggers(triggers)
        .build();
  }

  @Override
  protected ParallelGatewayState getTerminateElementState(ParallelGatewayState elementState) {
    return new ParallelGatewayState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getTriggeredFlows(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
