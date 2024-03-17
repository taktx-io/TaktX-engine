package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TaskProcessor extends ActivityProcessor<Task, TaskState> {
  private static final Logger LOG = Logger.getLogger(TaskProcessor.class);

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      Task element,
      TaskState oldState,
      Variables variables) {
    return new TriggerResult(
        new TaskState(ActivityStateEnum.FINISHED, oldState.getElementInstanceId()),
        Set.of(),
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  public TaskState initialState() {
    return new TaskState(ActivityStateEnum.READY, UUID.randomUUID());
  }
}
