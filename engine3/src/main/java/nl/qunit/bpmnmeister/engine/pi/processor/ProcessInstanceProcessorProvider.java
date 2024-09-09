package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.BaseElement2;
import nl.qunit.bpmnmeister.pd.model.CallActivity2;
import nl.qunit.bpmnmeister.pd.model.CatchEvent2;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics2;
import nl.qunit.bpmnmeister.pd.model.SendTask2;
import nl.qunit.bpmnmeister.pd.model.ServiceTask2;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;
import nl.qunit.bpmnmeister.pd.model.Task2;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;

@ApplicationScoped
public class ProcessInstanceProcessorProvider {

  @Inject StartEventInstanceProcessor startEventProcessor;
  //  @Inject IntermediateCatchEventProcessor intermediateCatchEventProcessor;
  //  @Inject IntermediateThrowEventProcessor intermediateThrowEventProcessor;
  @Inject EndEventInstanceProcessor endEventProcessor;
  //  @Inject ExclusiveGatewayProcessor exclusiveGatewayProcessor;
  //  @Inject ParallelGatewayProcessor parallelGatewayProcessor;
  //  @Inject InclusiveGatewayProcessor inclusiveGatewayProcessor;
  @Inject ServiceTaskInstanceProcessor serviceTaskProcessor;
  //  @Inject BoundaryEventProcessor boundaryEventProcessor;
  @Inject TaskInstanceProcessor taskProcessor;
  @Inject SubProcessInstanceProcessor subProcessProcessor;
  @Inject CallActivityInstanceProcessor callActivityProcessor;
  @Inject SendTaskInstanceProcessor sendTaskProcessor;
  //  @Inject ReceiveTaskProcessor receiveTaskProcessor;
  @Inject FeelExpressionHandler feelExpressionHandler;

  public FLowNodeInstanceProcessor<?, ?, ?> getProcessor(BaseElement2 element) {
    if (element instanceof ThrowEvent2 throwEvent) {
      return getProcessorForThrowEvent(throwEvent);
    } else if (element instanceof CatchEvent2 catchEvent) {
      return getProcessorForCatchEvent(catchEvent);
      //    } else if (element instanceof ExclusiveGateway2) {
      //      return exclusiveGatewayProcessor;
      //    } else if (element instanceof InclusiveGatewayD2) {
      //      return inclusiveGatewayProcessor;
      //    } else if (element instanceof Parallel) {
      //      return parallelGatewayProcessor;
    } else if (element instanceof Activity2 activity) {
      return getStateProcessorForActivity(activity);
    }

    throw new IllegalStateException("Unknown element type: " + element.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getProcessorForThrowEvent(ThrowEvent2 throwEvent) {
    if (throwEvent instanceof EndEvent2) {
      return endEventProcessor;
      //    } else if (throwEvent instanceof IntermediateThrowEvent) {
      //      return intermediateThrowEventProcessor;
    }
    throw new IllegalStateException("Unknown throw element type: " + throwEvent.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getProcessorForCatchEvent(CatchEvent2 element) {
    if (element instanceof StartEvent2) {
      return startEventProcessor;
      //    } else if (element instanceof IntermediateCatchEvent) {
      //      return intermediateCatchEventProcessor;
      //    } else if (element instanceof BoundaryEventDTO) {
      //      return boundaryEventProcessor;
    }
    throw new IllegalStateException("Unknown catch event element type: " + element.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getStateProcessorForActivity(Activity2 element) {
    ActivityInstanceProcessor<?, ?, ?> processor = null;
    if (element instanceof ServiceTask2) {
      processor = serviceTaskProcessor;
    } else if (element instanceof SendTask2) {
      processor = sendTaskProcessor;
    } else if (element instanceof SubProcess2) {
      processor = subProcessProcessor;
    } else if (element instanceof CallActivity2) {
      processor = callActivityProcessor;
      //    } else if (element instanceof ReceiveTaskDTO) {
      //      processor = receiveTaskProcessor;
    } else if (element instanceof Task2) {
      // This must be the last check, as Task is the superclass of all other tasks
      processor = taskProcessor;
    }
    if (!element.getLoopCharacteristics().equals(LoopCharacteristics2.NONE)) {
      // Wrap in MultiInstance processor when the element has loop characteristics
      return new MultiInstanceProcessor(feelExpressionHandler, processor);
    }
    return processor;
  }
}
