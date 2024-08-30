package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.ToString;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.TaskDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;

@ApplicationScoped
@ToString(callSuper = true)
public class TaskProcessor extends ActivityProcessor<TaskDTO<TaskState>, TaskState> {

  @Override
  protected TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      TaskDTO<TaskState> element,
      TaskState oldState,
      ScopedVars variables) {
    TaskState finishedTaskState =
        new TaskState(
            FlowNodeStateEnum.FINISHED,
            oldState.getParentElementInstanceId(),
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return finishActivity(
        TriggerResult.EMPTY, processInstance, definition, element, finishedTaskState, variables);
  }

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      TaskDTO<TaskState> element,
      TaskState taskState,
      ScopedVars variables) {
    return TriggerResult.EMPTY;
  }

  @Override
  protected TaskState getTerminateElementState(TaskState elementState) {
    return new TaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getParentElementInstanceId(),
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}
