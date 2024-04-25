package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import org.jboss.logging.Logger;

public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(ActivityProcessor.class);

  protected TriggerResult finishActivity(
      ProcessInstance processInstance,
      Activity element,
      ActivityState newState,
      Variables returnVariables) {
    if (!processInstance.getParentProcessInstanceKey().equals(ProcessInstanceKey.NONE)) {
      return new TriggerResult(
          newState,
          Set.of(),
          Set.of(),
          Set.of(
              new FlowElementTrigger(
                  processInstance.getParentProcessInstanceKey(),
                  ProcessInstanceKey.NONE,
                  processInstance.getParentElementId(),
                  ProcessDefinition.NONE,
                  Constants.NONE,
                  Constants.NONE,
                  processInstance.getVariables())),
          Set.of(),
          ThrowingEvent.NOOP,
          returnVariables);
    } else {
      return new TriggerResult(
          newState,
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          returnVariables);
    }
  }
}
