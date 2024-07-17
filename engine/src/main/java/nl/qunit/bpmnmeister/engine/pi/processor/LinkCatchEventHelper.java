package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;

@ApplicationScoped
public class LinkCatchEventHelper {

  public void processWhenReady(
      ProcessDefinition definition,
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      ProcessInstance processInstance,
      IntermediateCatchEvent element,
      ScopedVars variables,
      IntermediateCatchEventState oldState) {
    triggerResultBuilder
        .newFlowNodeStates(
            List.of(
                newStateBuilder
                    .state(FlowNodeStateEnum.FINISHED)
                    .passedCnt(oldState.getPassedCnt() + 1)
                    .build()))
        .processInstanceTriggers(
            TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                processInstance, definition, oldState, element));
  }
}
