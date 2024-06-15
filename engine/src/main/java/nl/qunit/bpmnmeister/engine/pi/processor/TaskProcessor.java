package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.ToString;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;

@ApplicationScoped
@ToString(callSuper = true)
public class TaskProcessor extends ActivityProcessor<Task<TaskState>, TaskState> {

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      Task<TaskState> element,
      TaskState oldState,
      ScopedVars variables) {
    return TriggerResult.builder()
        .newFlowNodeState(
            new TaskState(
                FlowNodeStateEnum.FINISHED,
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                oldState.getLoopCnt(),
                oldState.getInputFlowId()))
        .processInstanceTriggers(
            TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                processInstance, definition, element))
        .build();
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
