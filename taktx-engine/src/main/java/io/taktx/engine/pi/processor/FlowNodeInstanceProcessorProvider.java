/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import io.quarkus.security.User;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.BaseElement;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.EndEvent;
import io.taktx.engine.pd.model.ExclusiveGateway;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.InclusiveGateway;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.IntermediateThrowEvent;
import io.taktx.engine.pd.model.LoopCharacteristics;
import io.taktx.engine.pd.model.ParallelGateway;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pd.model.SendTask;
import io.taktx.engine.pd.model.ServiceTask;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.Task;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pd.model.UserTask;
import io.taktx.engine.pi.ProcessInstanceMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;

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
  @Inject @DefaultTaskProcessor TaskInstanceProcessor taskProcessor;
  @Inject SubProcessInstanceProcessor subProcessProcessor;
  @Inject CallActivityInstanceProcessor callActivityProcessor;
  @Inject SendTaskInstanceProcessor sendTaskProcessor;
  @Inject ReceiveTaskInstanceProcessor receiveTaskProcessor;
  @Inject FeelExpressionHandler feelExpressionHandler;
  @Inject ProcessInstanceMapper processInstanceMapper;
  @Inject Clock clock;
  @Inject @UserTaskProcessor UserTaskInstanceProcessor userTaskProcessor;

  public FlowNodeInstanceProcessor<?, ?, ?> getProcessor(BaseElement element) {
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

  private FlowNodeInstanceProcessor<?, ?, ?> getProcessorForGateway(Gateway gateway) {

    if (gateway instanceof ExclusiveGateway) {
      return exclusiveGatewayProcessor;
    } else if (gateway instanceof InclusiveGateway) {
      return inclusiveGatewayProcessor;
    } else if (gateway instanceof ParallelGateway) {
      return parallelGatewayProcessor;
    }
    throw new IllegalStateException("Unknown gateway element type: " + gateway.getClass());
  }

  private FlowNodeInstanceProcessor<?, ?, ?> getProcessorForThrowEvent(ThrowEvent throwEvent) {
    if (throwEvent instanceof EndEvent) {
      return endEventProcessor;
    } else if (throwEvent instanceof IntermediateThrowEvent) {
      return intermediateThrowEventProcessor;
    }
    throw new IllegalStateException("Unknown throw element type: " + throwEvent.getClass());
  }

  private FlowNodeInstanceProcessor<?, ?, ?> getProcessorForCatchEvent(CatchEvent element) {
    if (element instanceof StartEvent) {
      return startEventProcessor;
    } else if (element instanceof IntermediateCatchEvent) {
      return intermediateCatchEventProcessor;
    } else if (element instanceof BoundaryEvent) {
      return boundaryEventProcessor;
    }
    throw new IllegalStateException("Unknown catch event element type: " + element.getClass());
  }

  private FlowNodeInstanceProcessor<?, ?, ?> getStateProcessorForActivity(Activity element) {
    ActivityInstanceProcessor<?, ?, ?> processor = null;
    if (element instanceof ServiceTask) {
      processor = serviceTaskProcessor;
    } else if (element instanceof UserTask) {
      processor = userTaskProcessor;
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
          feelExpressionHandler, processor, processInstanceMapper, clock);
    }
    return processor;
  }
}
