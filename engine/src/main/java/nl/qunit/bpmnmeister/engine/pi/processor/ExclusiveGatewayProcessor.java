package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

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

    Set<String> outgoingFlows =
        getOutgoingFlowsMatchingConditionOrDefault(
            definition, element, variables, feelExpressionHandler);
    return TriggerResult.builder()
        .newFlowNodeState(
            new ExclusiveGatewayState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                FlowNodeStateEnum.FINISHED,
                oldState.getInputFlowId()))
        .newActiveFlows(outgoingFlows)
        .build();
  }

  @Override
  protected ExclusiveGatewayState getTerminateElementState(ExclusiveGatewayState elementState) {
    return new ExclusiveGatewayState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
