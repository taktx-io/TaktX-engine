package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SequentialMultiInstanceProcessor
    extends StateProcessor<MultiInstance, MultiInstanceState> {
  private static final Logger LOG = Logger.getLogger(SequentialMultiInstanceProcessor.class);

  @Override
  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      BaseElement element,
      BpmnElementState oldState,
      Variables variables) {

    if (trigger.isTerminate()) {
      return new TriggerResult(
          terminate((MultiInstanceState) oldState), Set.of(), Set.of(), Set.of(), Variables.EMPTY);
    }

    return switch (oldState.getState()) {
      case INIT -> triggerWhenInit(
          trigger, processInstance, (Activity) element, (MultiInstanceState) oldState, variables);
      case WAITING -> triggerWhenWaiting(
          trigger, processInstance, (Activity) element, (MultiInstanceState) oldState, variables);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  private TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    JsonNode inputCollection = variables.get(element.getLoopCharacteristics().getInputCollection());

    if (!inputCollection.isEmpty()) {
      return triggerIteration(
          trigger,
          processInstance,
          element,
          oldState,
          variables,
          oldState.getLoopCnt(),
          inputCollection);
    } else {
      // Empty collection just go to finished state
      return new TriggerResult(
          new MultiInstanceState(
              StateEnum.FINISHED, oldState.getElementInstanceId(), oldState.getLoopCnt()),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
  }

  private TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    JsonNode inputCollection = variables.get(element.getLoopCharacteristics().getInputCollection());

    int loopCnt = oldState.getLoopCnt() + 1;

    if (loopCnt < inputCollection.size()) {
      return triggerIteration(
          trigger, processInstance, element, oldState, variables, loopCnt, inputCollection);
    } else {
      // Finished iterating collection just go to finished state
      return new TriggerResult(
          new MultiInstanceState(StateEnum.FINISHED, oldState.getElementInstanceId(), loopCnt),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
  }

  private static TriggerResult triggerIteration(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables,
      int loopCnt,
      JsonNode inputCollection) {
    Variables activityVariables = variables.put("loopCnt", new IntNode(loopCnt));
    JsonNode inputElement = inputCollection.get(loopCnt);
    activityVariables =
        activityVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);

    ProcessInstanceTrigger newProcessInstanceTrigger =
        new ProcessInstanceTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            element.getId(),
            false,
            trigger.getInputFlowId(),
            activityVariables);

    return new TriggerResult(
        new MultiInstanceState(StateEnum.WAITING, oldState.getElementInstanceId(), loopCnt),
        Set.of(),
        Set.of(),
        Set.of(newProcessInstanceTrigger),
        activityVariables);
  }

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(StateEnum.INIT, UUID.randomUUID(), 0);
  }

  @Override
  public MultiInstanceState terminate(MultiInstanceState oldState) {
    return new MultiInstanceState(
        StateEnum.TERMINATED, oldState.getElementInstanceId(), oldState.getLoopCnt());
  }
}
