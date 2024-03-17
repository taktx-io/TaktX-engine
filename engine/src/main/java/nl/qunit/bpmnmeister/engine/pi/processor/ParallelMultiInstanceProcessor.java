package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;

@ApplicationScoped
public class ParallelMultiInstanceProcessor
    extends ActivityProcessor<Activity, MultiInstanceState> {
  @Override
  public TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {

    return switch (oldState.getState()) {
      case READY -> triggerWhenReady(
          trigger, processInstance, (Activity) element, (MultiInstanceState) oldState, variables);
      case ACTIVE -> triggerWhenActive(
          trigger, processInstance, (Activity) element, (MultiInstanceState) oldState, variables);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  private TriggerResult triggerWhenReady(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    JsonNode inputCollection = variables.get(element.getLoopCharacteristics().getInputCollection());
    Set<Trigger> subProcessTriggers = new HashSet<>();
    for (int i = 0; i < inputCollection.size(); i++) {
      Variables updatedVariables =
          variables.remove(element.getLoopCharacteristics().getInputCollection());
      updatedVariables = updatedVariables.put("loopCnt", new IntNode(i));
      JsonNode inputElement = inputCollection.get(i);
      updatedVariables =
          updatedVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);

      subProcessTriggers.add(
          new FlowElementTrigger(
              new ProcessInstanceKey(UUID.randomUUID()),
              processInstance.getProcessInstanceKey(),
              element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
              element.getId(),
              BaseElementId.NONE,
              updatedVariables));
    }
    return new TriggerResult(
        new MultiInstanceState(ActivityStateEnum.ACTIVE, oldState.getElementInstanceId(), 0),
        Set.of(),
        Set.of(),
        subProcessTriggers,
        Variables.EMPTY);
  }

  private TriggerResult triggerWhenActive(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    JsonNode inputCollection = variables.get(element.getLoopCharacteristics().getInputCollection());
    int loopsReceived = oldState.getLoopCnt();
    if (loopsReceived < inputCollection.size()) {
      return new TriggerResult(
          new MultiInstanceState(
              ActivityStateEnum.ACTIVE, oldState.getElementInstanceId(), loopsReceived + 1),
          Set.of(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    } else {
      MultiInstanceState newState =
          new MultiInstanceState(
              ActivityStateEnum.FINISHED, oldState.getElementInstanceId(), loopsReceived + 1);

      // all responses received. Finish the multi instance activity
      return finishActivity(processInstance, element, newState);
    }
  }

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(ActivityStateEnum.READY, UUID.randomUUID(), 0);
  }
}
