/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.client.annotation.TaktWorkerMethod;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * An ExternalTaskTriggerConsumer that scans for methods annotated with @TaktWorkerMethod and
 * invokes them when an external task is received.
 */
public class AnnotationScanningExternalTaskTriggerConsumer implements ExternalTaskTriggerConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AnnotationScanningExternalTaskTriggerConsumer.class);
  private final Map<String, Method> workerMethods = new HashMap<>();
  private final Map<String, Object> workerInstances = new HashMap<>();
  private final TaktParameterResolverFactory parameterResolverFactory;
  private final ProcessInstanceResponder externalTaskResponder;

  /**
   * Constructor
   *
   * @param parameterResolverFactory Factory to create parameter resolvers for method parameters
   * @param externalTaskResponder Responder to handle external task instances
   */
  public AnnotationScanningExternalTaskTriggerConsumer(
      TaktParameterResolverFactory parameterResolverFactory,
      ProcessInstanceResponder externalTaskResponder) {
    this.parameterResolverFactory = parameterResolverFactory;
    this.externalTaskResponder = externalTaskResponder;

    Set<Class<?>> annotatedClasses =
        AnnotationScanner.findClassesWithAnnotatedMethods(TaktWorkerMethod.class);
    log.info(
        "Found {} classes with @TaktWorkerMethod annotation: {}",
        annotatedClasses.size(),
        annotatedClasses.stream().map(Class::getName).collect(Collectors.joining(",")));
    for (Class<?> clazz : annotatedClasses) {
      Object instance = InstanceProvider.getInstance(clazz);
      Stream.of(clazz.getDeclaredMethods())
          .filter(m -> m.isAnnotationPresent(TaktWorkerMethod.class))
          .forEach(
              m -> {
                TaktWorkerMethod annotation = m.getAnnotation(TaktWorkerMethod.class);
                String taskId = annotation.taskId();
                workerMethods.put(taskId, m);
                workerInstances.put(taskId, instance);
              });
    }
  }

  @Override
  public Set<String> getJobIds() {
    return workerMethods.keySet();
  }

  @Override
  public void acceptBatch(List<ExternalTaskTriggerDTO> batch) {
    // group by task id
    Map<String, List<ExternalTaskTriggerDTO>> groupedByTaskId =
        batch.stream().collect(Collectors.groupingBy(ExternalTaskTriggerDTO::getExternalTaskId));

    groupedByTaskId.forEach(
        (taskId, tasks) -> {
          Method method = workerMethods.get(taskId);
          Object beanInstance = workerInstances.get(taskId);
          if (method == null || beanInstance == null) {
            throw new IllegalStateException(
                "No worker method or bean instance found for task ID: " + taskId);
          }
          TaktWorkerMethod workerMethod = method.getAnnotation(TaktWorkerMethod.class);
          boolean autoComplete = workerMethod.autoComplete();
          boolean methodIsVoid = method.getReturnType().equals(Void.TYPE);

          for (ExternalTaskTriggerDTO task : tasks) {
            processTask(task, method, autoComplete, beanInstance, methodIsVoid);
          }
        });
  }

  private void processTask(
      ExternalTaskTriggerDTO externalTaskTriggerDTO,
      Method method,
      boolean autoComplete,
      Object beanInstance,
      boolean methodIsVoid) {
    Object[] arguments = resolveParameters(method, externalTaskTriggerDTO);
    if (autoComplete) {
      // Rely on result and exceptions to determine success or failure

      try {
        Object result = method.invoke(beanInstance, arguments);
        if (methodIsVoid) {
          externalTaskResponder
              .responderForExternalTaskTrigger(externalTaskTriggerDTO)
              .respondSuccess();
        } else {
          externalTaskResponder
              .responderForExternalTaskTrigger(externalTaskTriggerDTO)
              .respondSuccess(result);
        }
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondError(false, "ERROR", e.getMessage());
      }
    } else {
      // Worker has to respond itself by Responder or TaktXClient. Result is ignored
      try {
        method.invoke(beanInstance, arguments);
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondError(false, "ERROR", e.getMessage());
      }
    }
  }

  private Object[] resolveParameters(Method method, ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    List<Object> result = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      TaktParameterResolver parameterResolver = parameterResolverFactory.create(parameter);
      Object resolved = parameterResolver.resolve(externalTaskTriggerDTO);
      result.add(resolved);
    }

    return result.toArray();
  }
}
