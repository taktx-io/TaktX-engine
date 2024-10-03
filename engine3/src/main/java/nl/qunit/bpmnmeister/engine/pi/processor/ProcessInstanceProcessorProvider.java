package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.InclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.pd.model.SendTask;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;

@ApplicationScoped
public class ProcessInstanceProcessorProvider {

  @Inject StartEventInstanceProcessor startEventProcessor;
  @Inject IntermediateCatchEventInstanceProcessor intermediateCatchEventProcessor;
  @Inject IntermediateThrowEventInstanceProcessor intermediateThrowEventProcessor;
  @Inject EndEventInstanceProcessor endEventProcessor;
  @Inject ExclusiveGatewayInstanceProcessor exclusiveGatewayProcessor;
  @Inject ParallelGatewayInstanceProcessor parallelGatewayProcessor;
  @Inject InclusiveGatewayInstanceProcessor inclusiveGatewayProcessor;
  @Inject ServiceTaskInstanceProcessor serviceTaskProcessor;
  @Inject BoundaryEventInstanceProcessor boundaryEventProcessor;
  @Inject TaskInstanceProcessor taskProcessor;
  @Inject SubProcessInstanceProcessor subProcessProcessor;
  @Inject CallActivityInstanceProcessor callActivityProcessor;
  @Inject SendTaskInstanceProcessor sendTaskProcessor;
  @Inject ReceiveTaskInstanceProcessor receiveTaskProcessor;
  @Inject FeelExpressionHandler feelExpressionHandler;
  @Inject VariablesMapper variablesMapper;

  public FLowNodeInstanceProcessor<?, ?, ?> getProcessor(BaseElement element) {
    if (element instanceof ThrowEvent throwEvent) {
      return getProcessorForThrowEvent(throwEvent);
    } else if (element instanceof CatchEvent catchEvent) {
      return getProcessorForCatchEvent(catchEvent);
    } else if (element instanceof Gateway gateway) {
      return getProcessorForGateway(gateway);
    } else if (element instanceof Activity activity) {
      return getStateProcessorForActivity(activity);
    }

    throw new IllegalStateException("Unknown element type: " + element.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getProcessorForGateway(Gateway gateway) {

    if (gateway instanceof ExclusiveGateway) {
      return exclusiveGatewayProcessor;
    } else if (gateway instanceof InclusiveGateway) {
      return inclusiveGatewayProcessor;
    } else if (gateway instanceof ParallelGateway) {
      return parallelGatewayProcessor;
    }
    throw new IllegalStateException("Unknown gateway element type: " + gateway.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getProcessorForThrowEvent(ThrowEvent throwEvent) {
    if (throwEvent instanceof EndEvent) {
      return endEventProcessor;
    } else if (throwEvent instanceof IntermediateThrowEvent) {
      return intermediateThrowEventProcessor;
    }
    throw new IllegalStateException("Unknown throw element type: " + throwEvent.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getProcessorForCatchEvent(CatchEvent element) {
    if (element instanceof StartEvent) {
      return startEventProcessor;
    } else if (element instanceof IntermediateCatchEvent) {
      return intermediateCatchEventProcessor;
    } else if (element instanceof BoundaryEvent) {
      return boundaryEventProcessor;
    }
    throw new IllegalStateException("Unknown catch event element type: " + element.getClass());
  }

  private FLowNodeInstanceProcessor<?, ?, ?> getStateProcessorForActivity(Activity element) {
    ActivityInstanceProcessor<?, ?, ?> processor = null;
    if (element instanceof ServiceTask) {
      processor = serviceTaskProcessor;
    } else if (element instanceof SendTask) {
      processor = sendTaskProcessor;
    } else if (element instanceof SubProcess) {
      processor = subProcessProcessor;
    } else if (element instanceof CallActivity) {
      processor = callActivityProcessor;
    } else if (element instanceof ReceiveTask) {
      processor = receiveTaskProcessor;
    } else if (element instanceof Task) {
      // This must be the last check, as Task is the superclass of all other tasks
      processor = taskProcessor;
    } else {
      throw new IllegalStateException("Unknown activity event element type: " + element.getClass());
    }
    if (!element.getLoopCharacteristics().equals(LoopCharacteristics.NONE)) {
      // Wrap in MultiInstance processor when the element has loop characteristics
      return new MultiInstanceProcessor(feelExpressionHandler, processor, variablesMapper);
    }
    return processor;
  }
}
