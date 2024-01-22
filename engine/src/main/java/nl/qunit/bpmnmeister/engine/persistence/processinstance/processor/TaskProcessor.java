package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Task;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.TaskState;

@ApplicationScoped
public class TaskProcessor extends StateProcessor<Task, TaskState> {

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      Task element,
      TaskState oldState) {
    return new TriggerResult(TaskState.builder().build(), element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      Task element,
      TaskState oldState) {
    return new TriggerResult(TaskState.builder().build(), element.getOutgoing());
  }

  @Override
  public TaskState initialState() {
    return TaskState.builder().state(StateEnum.INIT).build();
  }
}
