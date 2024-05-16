package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;

@ApplicationScoped
public class TaskProcessor extends ActivityProcessor<Task<TaskState>, TaskState> {

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      Task<TaskState> element,
      TaskState oldState,
      Variables variables) {
    return new TriggerResult(
        new TaskState(
            FlowNodeStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        variables);
  }

  @Override
  protected TaskState getTerminateElementState(TaskState elementState) {
    return new TaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}
