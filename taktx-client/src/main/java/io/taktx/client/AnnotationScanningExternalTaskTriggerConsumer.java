/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.JobWorker;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.topicmanagement.ExternalTaskTopicRequester;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
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
  private final ParameterResolverFactory parameterResolverFactory;
  private final ProcessInstanceResponder externalTaskResponder;

  /**
   * Constructor using the default PlainJavaInstanceProvider
   *
   * @param parameterResolverFactory Factory to create parameter resolvers for method parameters
   * @param externalTaskResponder Responder to handle external task instances
   * @param externalTaskTopicRequester Requester to manage external task topics
   * @param partitions THe number of partitions for the external task topic
   * @param cleanupPolicy The cleanup policy for the external task topic
   * @param replicationFactor The replication factor for the external task topic
   */
  public AnnotationScanningExternalTaskTriggerConsumer(
      ParameterResolverFactory parameterResolverFactory,
      ProcessInstanceResponder externalTaskResponder,
      ExternalTaskTopicRequester externalTaskTopicRequester,
      int partitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor) {
    this(
        parameterResolverFactory,
        externalTaskResponder,
        new PlainJavaInstanceProvider(),
        externalTaskTopicRequester,
        partitions,
        cleanupPolicy,
        replicationFactor);
  }

  /**
   * Constructor
   *
   * @param parameterResolverFactory Factory to create parameter resolvers for method parameters
   * @param externalTaskResponder Responder to handle external task instances
   * @param instanceProvider THe provider for worker bean instances
   * @param externalTaskTopicRequester Requester to manage external task topics
   * @param partitions THe number of partitions for the external task topic
   * @param cleanupPolicy The cleanup policy for the external task topic
   * @param replicationFactor The replication factor for the external task topic
   */
  public AnnotationScanningExternalTaskTriggerConsumer(
      ParameterResolverFactory parameterResolverFactory,
      ProcessInstanceResponder externalTaskResponder,
      WorkerBeanInstanceProvider instanceProvider,
      ExternalTaskTopicRequester externalTaskTopicRequester,
      int partitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor) {
    this.parameterResolverFactory = parameterResolverFactory;
    this.externalTaskResponder = externalTaskResponder;

    Set<Class<?>> annotatedClasses =
        AnnotationScanner.findClassesWithAnnotatedMethods(JobWorker.class);
    if (log.isInfoEnabled()) {
      log.info(
          "Found {} classes with @TaktWorkerMethod annotation: {}",
          annotatedClasses.size(),
          annotatedClasses.stream().map(Class::getName).collect(Collectors.joining(",")));
    }
    for (Class<?> clazz : annotatedClasses) {
      Object instance = instanceProvider.getInstance(clazz);
      Stream.of(clazz.getDeclaredMethods())
          .filter(m -> m.isAnnotationPresent(JobWorker.class))
          .forEach(
              m -> {
                JobWorker annotation = m.getAnnotation(JobWorker.class);
                String taskId = annotation.taskId();
                workerMethods.put(taskId, m);
                workerInstances.put(taskId, instance);
                externalTaskTopicRequester.requestExternalTaskTopic(
                    taskId, partitions, cleanupPolicy, replicationFactor);
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
          JobWorker workerMethod = method.getAnnotation(JobWorker.class);
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
      } catch (TaktXBpmnError e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondError(
                e.getAllowRetry(), e.getErrorCode(), e.getErrorMessage(), e.getVariables());
      } catch (TaktXBpmnEscalation e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondEscalation(e.getErrorCode(), e.getErrorMessage(), e.getVariables());
      } catch (TaktXBpmnPromise e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondPromise(e.getDuration());
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        // Convert stack trace to string array
        String[] stackTraceStrings =
            Arrays.stream(stackTrace)
                .map(stackTraceElement -> stackTraceElement.toString())
                .toArray(String[]::new);
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondIncident(e.getMessage(), stackTraceStrings);
      }
    } else {
      // Worker has to respond itself by Responder or TaktXClient. Result is ignored
      try {
        method.invoke(beanInstance, arguments);
      } catch (TaktXBpmnError e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondError(
                e.getAllowRetry(), e.getErrorCode(), e.getErrorMessage(), e.getVariables());
      } catch (TaktXBpmnEscalation e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondEscalation(e.getErrorCode(), e.getErrorMessage(), e.getVariables());
      } catch (TaktXBpmnPromise e) {
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondPromise(e.getDuration());
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        String[] stackTraceStrings =
            Arrays.stream(stackTrace)
                .map(stackTraceElement -> stackTraceElement.toString())
                .toArray(String[]::new);
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTriggerDTO)
            .respondIncident(e.getMessage(), stackTraceStrings);
      }
    }
  }

  private Object[] resolveParameters(Method method, ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    List<Object> result = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      ParameterResolver parameterResolver = parameterResolverFactory.create(parameter);
      Object resolved = parameterResolver.resolve(externalTaskTriggerDTO);
      result.add(resolved);
    }

    return result.toArray();
  }
}
