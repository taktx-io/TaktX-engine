package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SequentialMultiInstanceProcessor
    extends ActivityProcessor<Activity, MultiInstanceState> {
  private static final Logger LOG = Logger.getLogger(SequentialMultiInstanceProcessor.class);

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    if (oldState.getState() == ActivityStateEnum.READY) {
      return triggerFlowElementReady(trigger, processInstance, element, oldState, variables);
    } else if (oldState.getState() == ActivityStateEnum.ACTIVE) {
      return triggerFlowElementActive(trigger, processInstance, element, oldState, variables);
    }
    return null;
  }

  protected TriggerResult triggerFlowElementReady(
      FlowElementTrigger trigger,
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
              ActivityStateEnum.FINISHED, oldState.getElementInstanceId(), oldState.getLoopCnt()),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
  }

  protected TriggerResult triggerFlowElementActive(
      FlowElementTrigger trigger,
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
      return finishActivity(processInstance, element, oldState);
    }
  }

  private static TriggerResult triggerIteration(
      FlowElementTrigger trigger,
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

    FlowElementTrigger newProcessInstanceTrigger =
        new FlowElementTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            element.getId(),
            BaseElementId.NONE,
            activityVariables);

    return new TriggerResult(
        new MultiInstanceState(ActivityStateEnum.ACTIVE, oldState.getElementInstanceId(), loopCnt),
        Set.of(),
        Set.of(),
        Set.of(newProcessInstanceTrigger),
        activityVariables);
  }

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(ActivityStateEnum.READY, UUID.randomUUID(), 0);
  }
}
