package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.processor.flowelement.BoundaryEventProcessor;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.InclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.SendTask;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@ApplicationScoped
public class ProcessorProvider {

  @Inject StartEventProcessor startEventProcessor;
  @Inject IntermediateCatchEventProcessor intermediateCatchEventProcessor;
  @Inject EndEventProcessor endEventProcessor;
  @Inject ExclusiveGatewayProcessor exclusiveGatewayProcessor;
  @Inject ParallelGatewayProcessor parallelGatewayProcessor;
  @Inject InclusiveGatewayProcessor inclusiveGatewayProcessor;
  @Inject ServiceTaskProcessor serviceTaskProcessor;
  @Inject BoundaryEventProcessor boundaryEventProcessor;
  @Inject TaskProcessor taskProcessor;
  @Inject SubProcessProcessor subProcessProcessor;
  @Inject CallActivityProcessor callActivityProcessor;
  @Inject SendTaskProcessor sendTaskProcessor;

  public StateProcessor<?, ?> getProcessor(BaseElement element) {
    if (element instanceof CatchEvent<?> catchEvent) {
      return getProcessorForCatchEvent(catchEvent);
    } else if (element instanceof EndEvent) {
      return endEventProcessor;
    } else if (element instanceof ExclusiveGateway) {
      return exclusiveGatewayProcessor;
    } else if (element instanceof InclusiveGateway) {
      return inclusiveGatewayProcessor;
    } else if (element instanceof ParallelGateway) {
      return parallelGatewayProcessor;
    } else if (element instanceof Activity activity) {
      return getStateProcessorForActivity(activity);
    }

    throw new IllegalStateException("Unknown element type: " + element.getClass());
  }

  private StateProcessor<?, ?> getProcessorForCatchEvent(CatchEvent<?> element) {
    if (element instanceof StartEvent) {
      return startEventProcessor;
    } else if (element instanceof IntermediateCatchEvent) {
      return intermediateCatchEventProcessor;
    } else if (element instanceof BoundaryEvent) {
      return boundaryEventProcessor;
    }
    throw new IllegalStateException("Unknown catch event element type: " + element.getClass());
  }

  private StateProcessor<? extends BaseElement, ? extends FlowNodeState>
      getStateProcessorForActivity(Activity element) {
    ActivityProcessor<? extends Activity, ? extends FlowNodeState> processor = null;
    if (element instanceof ServiceTask) {
      processor = serviceTaskProcessor;
    } else if (element instanceof SendTask) {
      processor = sendTaskProcessor;
    } else if (element instanceof SubProcess) {
      processor = subProcessProcessor;
    } else if (element instanceof CallActivity) {
      processor = callActivityProcessor;
    } else if (element instanceof Task) {
      // This must be the last check, as Task is the superclass of all other tasks
      processor = taskProcessor;
    }
    return processor;
  }
}
