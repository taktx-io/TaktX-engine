package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TaskProcessor extends ActivityProcessor<Task, TaskState> {
  private static final Logger LOG = Logger.getLogger(TaskProcessor.class);

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Task element,
      TaskState oldState,
      Variables variables) {
    return new TriggerResult(
        new TaskState(StateEnum.FINISHED, UUID.randomUUID()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Task element,
      TaskState oldState,
      Variables variables) {
    // Should not happen
    throw new IllegalStateException("Task should not be in waiting state");
  }

  @Override
  public TaskState initialState() {
    return new TaskState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public TaskState terminate(TaskState taskState) {
    return new TaskState(StateEnum.TERMINATED, taskState.getElementInstanceId());
  }
}
