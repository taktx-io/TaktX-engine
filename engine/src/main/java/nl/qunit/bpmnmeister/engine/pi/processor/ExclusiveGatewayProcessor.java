package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGateway, ExclusiveGatewayState> {

  private final FeelExpressionHandler feelExpressionHandler;

  public ExclusiveGatewayProcessor(FeelExpressionHandler feelExpressionHandler) {
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState,
      Variables variables) {

    List<SequenceFlow> sequenceFlows =
        definition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getOutgoingSequenceFlowsForElement(element);
    Set<String> outgoingFlows = new HashSet<>();
    Optional<SequenceFlow> flowWithCondition =
        sequenceFlows.stream()
            .filter(sequenceFlow -> !FlowCondition.NONE.equals(sequenceFlow.getCondition()))
            .filter(
                sequenceFlow ->
                    feelExpressionHandler
                        .processFeelExpression(
                            sequenceFlow.getCondition().getExpression(), variables)
                        .asBoolean())
            .findFirst();
    if (flowWithCondition.isPresent()) {
      outgoingFlows.add(flowWithCondition.get().getId());
    } else if (!Constants.NONE.equals(element.getDefaultFlow())) {
      outgoingFlows.add(element.getDefaultFlow());
    } else if (sequenceFlows.size() == 1
        && FlowCondition.NONE.equals(sequenceFlows.iterator().next().getCondition())) {
      // Last chance, if no condition is met and no default flow is set, take the only outgoing flow
      // but only if there is no condition on the flow
      outgoingFlows.add(sequenceFlows.iterator().next().getId());
    }
    if (outgoingFlows.isEmpty()) {
      throw new IllegalStateException(
          "No outgoing flow could be selected found for exclusive gateway: " + element.getId());
    }
    return new TriggerResult(
        new ExclusiveGatewayState(
            UUID.randomUUID(), oldState.getPassedCnt() + 1, ActivityStateEnum.ACTIVE),
        outgoingFlows,
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }
}
