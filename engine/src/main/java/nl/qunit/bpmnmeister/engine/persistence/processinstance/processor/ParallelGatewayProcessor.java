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
  protected TriggerResult triggerWhenActive(Trigger trigger, Definitions processDefinition, ParallelGateway element, ParallelGatewayState oldState) {
    return getTriggerResult(trigger, element, oldState);
  }

  @Override
  protected TriggerResult triggerWhenInit(Trigger trigger, Definitions processDefinition, ParallelGateway element, ParallelGatewayState oldState) {
    return getTriggerResult(trigger, element, oldState);
  }

  private static TriggerResult getTriggerResult(Trigger trigger, ParallelGateway element, ParallelGatewayState oldState) {
    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.inputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    StateEnum newState = StateEnum.ACTIVE;
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newState = StateEnum.INIT;
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
