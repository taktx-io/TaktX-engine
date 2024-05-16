package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartThrowingEvent;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.StartEventState;

@ApplicationScoped
public class StartEventProcessor extends EventProcessor<StartEvent, StartEventState> {
  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      StartEvent element,
      StartEventState oldState) {
    return TriggerResult.builder()
        .newFlowNodeState(
            new StartEventState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                FlowNodeStateEnum.FINISHED,
                oldState.getInputFlowId()))
        .newActiveFlows(element.getOutgoing())
        .throwingEvent(new StartThrowingEvent())
        .build();
  }

  @Override
  protected StartEventState getTerminateElementState(StartEventState elementState) {
    return new StartEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
