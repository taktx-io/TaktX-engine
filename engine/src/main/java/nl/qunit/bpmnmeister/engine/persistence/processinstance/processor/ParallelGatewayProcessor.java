package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ParallelGateway;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.ParallelGatewayState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends StateProcessor<ParallelGateway, ParallelGatewayState> {
  @Override
  public TriggerResult doTrigger(
      Trigger trigger,
      Definitions processDefinition,
      ParallelGateway element,
      ParallelGatewayState oldState) {
    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.inputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    StateEnum newState = StateEnum.ACTIVE;
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newState = StateEnum.FINISHED;
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
    }
    return new TriggerResult(
        ParallelGatewayState.builder().triggeredFlows(newTriggeredFlows).state(newState).build(),
        outputFlows);
  }

  @Override
  public ParallelGatewayState initialState() {
    return ParallelGatewayState.builder()
        .state(StateEnum.INIT)
        .triggeredFlows(new HashSet<>())
        .build();
  }
}
