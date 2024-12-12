package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.Activity;
import com.flomaestro.engine.pd.model.BaseElement;
import com.flomaestro.engine.pd.model.BoundaryEvent;
import com.flomaestro.engine.pd.model.CallActivity;
import com.flomaestro.engine.pd.model.CatchEvent;
import com.flomaestro.engine.pd.model.EndEvent;
import com.flomaestro.engine.pd.model.ExclusiveGateway;
import com.flomaestro.engine.pd.model.Gateway;
import com.flomaestro.engine.pd.model.InclusiveGateway;
import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
import com.flomaestro.engine.pd.model.LoopCharacteristics;
import com.flomaestro.engine.pd.model.ParallelGateway;
import com.flomaestro.engine.pd.model.ReceiveTask;
import com.flomaestro.engine.pd.model.SendTask;
import com.flomaestro.engine.pd.model.ServiceTask;
import com.flomaestro.engine.pd.model.StartEvent;
import com.flomaestro.engine.pd.model.SubProcess;
import com.flomaestro.engine.pd.model.Task;
import com.flomaestro.engine.pd.model.ThrowEvent;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FlowNodeInstanceProcessorProvider {

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
  @Inject ProcessInstanceMapper processInstanceMapper;
  @Inject ObjectMapper objectMapper;

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
      return new MultiInstanceProcessor(
          feelExpressionHandler, processor, variablesMapper, processInstanceMapper, objectMapper);
    }
    return processor;
  }
}
