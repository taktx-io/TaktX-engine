package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGateway, ExclusiveGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState,
      ScopedVars variables) {

    return TriggerResult.builder()
        .newFlowNodeState(
            new ExclusiveGatewayState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                FlowNodeStateEnum.FINISHED,
                oldState.getInputFlowId()))
        .processInstanceTriggers(
            getProcessInstanceTriggers(definition, processInstance, element, variables))
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
