package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGatewayDTO, ExclusiveGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      ExclusiveGatewayDTO element,
      ExclusiveGatewayState oldState,
      ScopedVars variables) {

    return TriggerResult.builder()
        .newFlowNodeStates(
            List.of(
                new ExclusiveGatewayState(
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    oldState.getPassedCnt() + 1,
                    FlowNodeStateEnum.FINISHED,
                    oldState.getInputFlowId())))
        .processInstanceTriggers(
            getProcessInstanceTriggers(definition, processInstance, element, oldState, variables))
        .build();
  }

  @Override
  protected ExclusiveGatewayState getTerminateElementState(ExclusiveGatewayState elementState) {
    return new ExclusiveGatewayState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
