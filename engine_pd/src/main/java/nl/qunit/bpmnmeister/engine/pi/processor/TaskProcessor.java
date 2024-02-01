package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TaskProcessor extends StateProcessor<Task, TaskState> {
  private static final Logger LOG = Logger.getLogger(StartEventProcessor.class);

  @Override
  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      Task element,
      TaskState oldState) {
    throw new IllegalStateException("Task cannot be in finished state");
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      Task element,
      TaskState oldState) {
    throw new IllegalStateException("Task cannot be in waiting state");
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      Task element,
      TaskState oldState) {
    throw new IllegalStateException("Task cannot be in active state");
  }

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      Task element,
      TaskState oldState) {
    //    LOG.info("Triggering Task event in Init state " + element.getId() + " for process
    // definition " + processDefinition + " in process instance" + trigger.getProcessInstanceKey());
    return TriggerResult.builder()
        .newElementState(TaskState.builder().state(StateEnum.INIT).build())
        .newActiveFlows(element.getOutgoing())
        .build();
  }

  @Override
  public TaskState initialState() {
    return TaskState.builder().state(StateEnum.INIT).build();
  }
}
