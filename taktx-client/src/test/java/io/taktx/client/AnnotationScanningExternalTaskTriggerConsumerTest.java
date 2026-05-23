/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.JobWorker;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.variables.Variables;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnotationScanningExternalTaskTriggerConsumerTest {

  private static final String JOB_TYPE = "list-binding-consumer-test";

  private ProcessInstanceResponder externalTaskResponder;
  private ExternalTaskInstanceResponder taskInstanceResponder;
  private ExternalTaskTopicRequestGateway externalTaskTopicRequestGateway;
  private ListBindingWorker worker;
  private AnnotationScanningExternalTaskTriggerConsumer consumer;

  @BeforeEach
  void setUp() {
    externalTaskResponder = mock(ProcessInstanceResponder.class);
    taskInstanceResponder = mock(ExternalTaskInstanceResponder.class);
    externalTaskTopicRequestGateway = mock(ExternalTaskTopicRequestGateway.class);
    worker = new ListBindingWorker();

    when(externalTaskResponder.responderForExternalTaskTrigger(any(ExternalTaskTriggerDTO.class)))
        .thenReturn(taskInstanceResponder);

    consumer =
        new AnnotationScanningExternalTaskTriggerConsumer(
            new DefaultParameterResolverFactory(externalTaskResponder),
            new DefaultResultProcessorFactory(),
            externalTaskResponder,
            new SingleWorkerInstanceProvider(worker),
            externalTaskTopicRequestGateway,
            1,
            CleanupPolicy.DELETE,
            (short) 1);
  }

  @Test
  void acceptBatch_resolvesTopLevelListParameterByVariableName() {
    String variableName = ListBindingWorker.parameterNameForInvoiceIds();
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .externalTaskId(JOB_TYPE)
            .variables(
                VariablesDTO.ofVariableMap(
                    Variables.map(variableName, List.of("INV-1", "INV-2", "INV-3"))))
            .build();

    consumer.acceptBatch(List.of(trigger));

    assertThat(consumer.getJobIds()).contains(JOB_TYPE);
    assertThat(worker.receivedInvoiceIds()).containsExactly("INV-1", "INV-2", "INV-3");
    verify(externalTaskTopicRequestGateway)
        .requestExternalTaskTopic(JOB_TYPE, 1, CleanupPolicy.DELETE, (short) 1);
    verify(externalTaskResponder).responderForExternalTaskTrigger(trigger);
    verify(taskInstanceResponder).respondSuccess((Object) any());
  }

  static final class SingleWorkerInstanceProvider implements WorkerBeanInstanceProvider {

    private final ListBindingWorker worker;

    SingleWorkerInstanceProvider(ListBindingWorker worker) {
      this.worker = worker;
    }

    @Override
    public <T> T getInstance(Class<T> clazz) {
      if (clazz == ListBindingWorker.class) {
        return clazz.cast(worker);
      }
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(
            "Unable to create worker instance for " + clazz.getName(), e);
      }
    }
  }

  static final class ListBindingWorker {

    private List<String> receivedInvoiceIds = List.of();

    @JobWorker(type = JOB_TYPE)
    public Map<String, Object> process(List<String> invoiceIds) {
      this.receivedInvoiceIds = List.copyOf(invoiceIds);
      return Map.of("processed", true, "count", invoiceIds.size());
    }

    List<String> receivedInvoiceIds() {
      return receivedInvoiceIds;
    }

    static String parameterNameForInvoiceIds() {
      try {
        return ListBindingWorker.class
            .getDeclaredMethod("process", List.class)
            .getParameters()[0]
            .getName();
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Unable to inspect parameter names for process", e);
      }
    }
  }
}
