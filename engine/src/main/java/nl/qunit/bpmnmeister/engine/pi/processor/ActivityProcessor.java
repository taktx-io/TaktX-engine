package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import org.jboss.logging.Logger;

public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(ActivityProcessor.class);

  public TriggerResult dotrigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables) {
    if (trigger.isTerminate()) {
      return new TriggerResult(
          terminate((S) oldState), Set.of(), Set.of(), Set.of(), Variables.EMPTY);
    }

    return switch (oldState.getState()) {
      case READY -> triggerWhenInit(trigger, processInstance, (E) element, (S) oldState, variables);
      case ACTIVE -> triggerWhenWaiting(
          trigger, processInstance, (E) element, (S) oldState, variables);
      case FINISHED -> triggerWhenFinished(
          trigger, processInstance, (E) element, (S) oldState, variables);
      case TERMINATED -> triggerWhenTerminated(
          trigger, processInstance, (E) element, (S) oldState, variables);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  protected abstract TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables);

  protected abstract TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables);

  protected TriggerResult triggerWhenTerminated(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables) {
    LOG.error("Triggering terminated activity: " + element.getId());

    return new TriggerResult(oldState, Set.of(), Set.of(), Set.of(), Variables.EMPTY);
  }

  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables) {
    LOG.error("Triggering finished activity: " + element.getId());
    return new TriggerResult(oldState, Set.of(), Set.of(), Set.of(), Variables.EMPTY);
  }
}
