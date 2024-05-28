package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Slf4j
@ToString
public abstract class StateProcessor<E extends BaseElement, S extends FlowNodeState> {

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode<?> element,
      Variables variables) {
    log.info("Trigger processor: " + this);
    Optional<FlowNodeState> optFlowNodeState =
        processInstance.getFlowNodeStates().get(element.getId());

    FlowNodeState flowNodeState = optFlowNodeState.orElse(null);
    if (trigger instanceof FlowElementTrigger flowElementTrigger) {
      if (flowNodeState == null) {
        flowNodeState = element.getInitialState(flowElementTrigger.getInputFlowId(), 0);
      } else if (flowNodeState.getState().isFinished()) {
        flowNodeState =
            element.getInitialState(
                flowElementTrigger.getInputFlowId(), flowNodeState.getPassedCnt());
      }
      return triggerFlowElement(
          flowElementTrigger,
          processInstance,
          definition,
          (E) element,
          (S) flowNodeState,
          variables);
    } else if (trigger instanceof ExternalTaskResponseTrigger externalTaskResponse) {
      return triggerExternalTaskResponse(
          externalTaskResponse,
          processInstance,
          definition,
          (E) element,
          (S) flowNodeState,
          variables);
    } else if (trigger instanceof TerminateTrigger terminateTrigger) {
      if (flowNodeState != null) {
        return terminate(processInstance, terminateTrigger, (E) element, (S) flowNodeState);
      } else {
        return TriggerResult.EMPTY;
      }
    }
    throw new IllegalStateException("Unknown trigger type: " + trigger);
  }

  protected abstract TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables);

  protected TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables) {
    return TriggerResult.builder().newFlowNodeState(oldState).build();
  }

  public TriggerResult terminate(
      ProcessInstance processInstance,
      TerminateTrigger terminateTrigger,
      E flowElement,
      S elementState) {
    return TriggerResult.builder().newFlowNodeState(getTerminateElementState(elementState)).build();
  }

  protected abstract S getTerminateElementState(S elementState);
}
