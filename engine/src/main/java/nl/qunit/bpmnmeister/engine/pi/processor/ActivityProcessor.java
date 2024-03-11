package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.jboss.logging.Logger;

public abstract class ActivityProcessor<E extends BaseElement, S extends BpmnElementState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(ActivityProcessor.class);

  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      BaseElement element,
      BpmnElementState oldState,
      Map<String, JsonNode> variables) {
    if (trigger.isTerminate()) {
      return new TriggerResult(terminate((S) oldState), Set.of(), Set.of(), Set.of(), Map.of());
    }

    return switch (oldState.getState()) {
      case INIT -> triggerWhenInit(trigger, processInstance, (E) element, (S) oldState, variables);
      case WAITING -> triggerWhenWaiting(
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
      Map<String, JsonNode> variables);

  protected abstract TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Map<String, JsonNode> variables);

  protected TriggerResult triggerWhenTerminated(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Map<String, JsonNode> variables) {
    LOG.error("Triggering terminated activity: " + element.getId());

    return new TriggerResult(oldState, Set.of(), Set.of(), Set.of(), Map.of());
  }

  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Map<String, JsonNode> variables) {
    LOG.error("Triggering finished activity: " + element.getId());
    return new TriggerResult(oldState, Set.of(), Set.of(), Set.of(), Map.of());
  }
}
