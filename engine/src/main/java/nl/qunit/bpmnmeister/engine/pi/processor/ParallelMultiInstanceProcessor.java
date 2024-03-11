package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
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

@ApplicationScoped
public class ParallelMultiInstanceProcessor
    extends StateProcessor<MultiInstance, MultiInstanceState> {

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
    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();
    for (int i = 0; i < inputCollection.size(); i++) {
      Variables updatedVariables =
          variables.remove(element.getLoopCharacteristics().getInputCollection());
      updatedVariables = updatedVariables.put("loopCnt", new IntNode(i));
      JsonNode inputElement = inputCollection.get(i);
      updatedVariables =
          updatedVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);

      subProcessTriggers.add(
          new ProcessInstanceTrigger(
              new ProcessInstanceKey(UUID.randomUUID()),
              processInstance.getProcessInstanceKey(),
              element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
              element.getId(),
              false,
              trigger.getInputFlowId(),
              updatedVariables));
    }
    return new TriggerResult(
        new MultiInstanceState(StateEnum.WAITING, oldState.getElementInstanceId(), 0),
        Set.of(),
        Set.of(),
        subProcessTriggers,
        Variables.EMPTY);
  }

  private TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    JsonNode inputCollection = variables.get(element.getLoopCharacteristics().getInputCollection());
    int loopsReceived = oldState.getLoopCnt() + 1;
    if (loopsReceived < inputCollection.size()) {
      return new TriggerResult(
          new MultiInstanceState(StateEnum.WAITING, oldState.getElementInstanceId(), loopsReceived),
          Set.of(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    } else {
      return new TriggerResult(
          new MultiInstanceState(
              StateEnum.FINISHED, oldState.getElementInstanceId(), loopsReceived),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
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
