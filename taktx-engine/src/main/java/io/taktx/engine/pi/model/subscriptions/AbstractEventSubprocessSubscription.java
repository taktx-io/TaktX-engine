package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public abstract class AbstractEventSubprocessSubscription extends Subscription {
  private int order;
  private SubProcess eventSubProcess;
  private StartEvent startEvent;

  @Override
  public void process(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> parentInstance) {
    // First terminate any active elements
    if (startEvent.isInterrupting()) {
      Map<Long, FlowNodeInstance<?>> allInstances = scope.getFlowNodeInstances().getAllInstances();
      for (FlowNodeInstance<?> instance : allInstances.values()) {
        if (instance.isActive()) {
          scope.getDirectInstanceResult().addAbortInstance(instance);
        }
      }
    }
    // Create a new instance for the event subprocess
    FlowNodeInstance<?> eventSubProcessInstance =
        eventSubProcess.createAndStoreNewInstance(scope.getParentFlowNodeInstance(), scope);
    VariableScope childVariableScope = variableScope.selectChildScope(eventSubProcessInstance);
    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(eventSubProcessInstance, null, childVariableScope);
    scope.getDirectInstanceResult().addNewFlowNodeInstance(startFlowNodeInstanceInfo);

    if (startEvent.isInterrupting()) {
      scope.getDirectInstanceResult().addAbortInstance(event.getCurrentInstance());
    }
  }

  @Override
  public int order() {
    return order;
  }

  /**
   * Checks if an event originated from within a specific event subprocess. This prevents infinite
   * loops where an event subprocess triggers an event that would trigger itself again.
   *
   * <p>Examples of scenarios this prevents:
   *
   * <ul>
   *   <li>Error Event Subprocess throws an error → would match itself
   *   <li>Escalation Event Subprocess throws an escalation → would match itself
   *   <li>Signal Event Subprocess throws a signal → would match itself
   * </ul>
   *
   * <p>Works for both interrupting and non-interrupting event subprocesses.
   *
   * @param event The event signal (error, escalation, signal, message, etc.)
   * @param eventSubProcess The event subprocess to check
   * @return true if the event originated from within the event subprocess
   */
  protected static boolean eventOriginatesFromSubProcess(
      EventSignal event, SubProcess eventSubProcess) {
    // Check the path to source to see if any element in the path is this event subprocess
    for (FlowNodeInstance<?> instanceInPath : event.getPathToSource()) {
      if (instanceInPath instanceof SubProcessInstance subProcessInstance) {
        // Check if this subprocess instance is an instance of the event subprocess we're checking
        if (subProcessInstance.getFlowNode().getId().equals(eventSubProcess.getId())) {
          return true; // Event originated from within this event subprocess
        }
      }
    }
    return false;
  }

  @Override
  public void cancel(Scope scope, IFlowNodeInstance fni) {
    // No-op for event subprocesses
  }
}
