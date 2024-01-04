package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Task;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.TaskState;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class TaskProcessor extends StateProcessor<Task, TaskState> {

  @Override
  protected TriggerResult triggerWhenActive(Trigger trigger, Definitions processDefinition, Task element, TaskState oldState) {
    return new TriggerResult(TaskState.builder().build(), element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenInit(Trigger trigger, Definitions processDefinition, Task element, TaskState oldState) {
    return new TriggerResult(TaskState.builder().build(), element.getOutgoing());
  }

  @Override
  public TaskState initialState() {
    return TaskState.builder().state(StateEnum.INIT).build();
  }
}
