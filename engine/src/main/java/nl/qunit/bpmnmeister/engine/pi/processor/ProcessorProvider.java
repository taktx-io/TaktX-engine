package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@ApplicationScoped
public class ProcessorProvider {

  @Inject StartEventProcessor startEventProcessor;
  @Inject EndEventProcessor endEventProcessor;
  @Inject ExclusiveGatewayProcessor exclusiveGatewayProcessor;
  @Inject ParallelGatewayProcessor parallelGatewayProcessor;
  @Inject ServiceTaskProcessor serviceTaskProcessor;
  @Inject TaskProcessor taskProcessor;
  @Inject SubProcessProcessor subProcessProcessor;
  @Inject SequentialMultiInstanceProcessor sequentialMultiInstanceProcessor;
  @Inject ParallelMultiInstanceProcessor parallelMultiInstanceProcessor;

  public StateProcessor<?, ?> getProcessor(BaseElement element) {
    if (element instanceof StartEvent) {
      return startEventProcessor;
    } else if (element instanceof EndEvent) {
      return endEventProcessor;
    } else if (element instanceof ExclusiveGateway) {
      return exclusiveGatewayProcessor;
    } else if (element instanceof ParallelGateway) {
      return parallelGatewayProcessor;
    } else if (element instanceof Activity activity) {
      return getStateProcessorForActivity(element, activity);
    }

    throw new IllegalStateException("Unknown element type: " + element.getClass());
  }

  private StateProcessor<? extends BaseElement, ? extends BpmnElementState>
      getStateProcessorForActivity(BaseElement element, Activity activity) {
    ActivityProcessor<? extends Activity, ? extends BpmnElementState> processor = null;
    if (element instanceof ServiceTask) {
      processor = serviceTaskProcessor;
    } else if (element instanceof Task) {
      processor = taskProcessor;
    } else if (element instanceof SubProcess) {
      processor = subProcessProcessor;
    }
    if (!activity.getLoopCharacteristics().equals(LoopCharacteristics.NONE)) {
      if (activity.getLoopCharacteristics().getIsSequential()) {
        return sequentialMultiInstanceProcessor;
      } else {
        return parallelMultiInstanceProcessor;
      }
    } else {
      return processor;
    }
  }
}
