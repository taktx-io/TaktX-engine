/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.AckStrategy;
import io.taktx.client.annotation.JobWorker;
import io.taktx.client.annotation.ThreadingStrategy;
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
  private final Map<String, AckStrategy> ackStrategies = new HashMap<>();
  private final Map<String, ThreadingStrategy> threadingStrategies = new HashMap<>();
  private final ParameterResolverFactory parameterResolverFactory;
  private final ResultProcessorFactory resultProcessorFactory;
  private final ProcessInstanceResponder externalTaskResponder;

  /**
   * Constructor using the default PlainJavaInstanceProvider
   *
   * @param parameterResolverFactory Factory to create parameter resolvers for method parameters
   * @param resultProcessorFactory Factory to create result processor for method return value
   * @param externalTaskResponder Responder to handle external task instances
   * @param externalTaskTopicRequester Requester to manage external task topics
   * @param partitions THe number of partitions for the external task topic
   * @param cleanupPolicy The cleanup policy for the external task topic
   * @param replicationFactor The replication factor for the external task topic
   */
  public AnnotationScanningExternalTaskTriggerConsumer(
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory,
      ProcessInstanceResponder externalTaskResponder,
      ExternalTaskTopicRequester externalTaskTopicRequester,
      int partitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor) {
    this(
        parameterResolverFactory,
        resultProcessorFactory,
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
   * @param resultProcessorFactory Factory to create result processor for method parameters
   * @param externalTaskResponder Responder to handle external task instances
   * @param instanceProvider THe provider for worker bean instances
   * @param externalTaskTopicRequester Requester to manage external task topics
   * @param partitions THe number of partitions for the external task topic
   * @param cleanupPolicy The cleanup policy for the external task topic
   * @param replicationFactor The replication factor for the external task topic
   */
  public AnnotationScanningExternalTaskTriggerConsumer(
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory,
      ProcessInstanceResponder externalTaskResponder,
      WorkerBeanInstanceProvider instanceProvider,
      ExternalTaskTopicRequester externalTaskTopicRequester,
      int partitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor) {
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;

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
                String type = annotation.type().replaceAll("[^a-zA-Z0-9._-]", "_");
                workerMethods.put(type, m);
                workerInstances.put(type, instance);
                ackStrategies.put(type, annotation.ackStrategy());
                threadingStrategies.put(type, annotation.threadingStrategy());
                externalTaskTopicRequester.requestExternalTaskTopic(
                    type, partitions, cleanupPolicy, replicationFactor);
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

          for (ExternalTaskTriggerDTO task : tasks) {
            processTask(task, method, autoComplete, beanInstance);
          }
        });
  }

  /**
   * Get the AckStrategy for the given task ID
   *
   * @param taskId The task ID
   * @return The AckStrategy
   */
  public AckStrategy getAckStrategy(String taskId) {
    return ackStrategies.getOrDefault(taskId, AckStrategy.EXPLICIT_BATCH);
  }

  /**
   * Get the ThreadingStrategy for the given task ID
   *
   * @param taskId The task ID
   * @return The ThreadingStrategy
   */
  public ThreadingStrategy getThreadingStrategy(String taskId) {
    return threadingStrategies.getOrDefault(taskId, ThreadingStrategy.VIRTUAL_THREAD_WAIT);
  }

  private void processTask(
      ExternalTaskTriggerDTO externalTaskTrigger,
      Method method,
      boolean autoComplete,
      Object beanInstance) {
    Object[] arguments = resolveParameters(method, externalTaskTrigger);
    if (autoComplete) {
      // Rely on result and exceptions to determine success or failure

      try {
        Object result = method.invoke(beanInstance, arguments);
        Object resolvedResult = processResult(method, result, externalTaskTrigger);
        externalTaskResponder
            .responderForExternalTaskTrigger(externalTaskTrigger)
            .respondSuccess(resolvedResult);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        switch (cause) {
          case TaktXBpmnError bpmnError -> respondError(externalTaskTrigger, bpmnError);
          case TaktXBpmnEscalation bpmnEscalation ->
              respondEscalation(externalTaskTrigger, bpmnEscalation);
          case TaktXBpmnPromise bpmnPromise -> respondPromis(externalTaskTrigger, bpmnPromise);
          default -> {
            StackTraceElement[] stackTrace = cause.getStackTrace();
            // Convert stack trace to string array
            respondIncident(externalTaskTrigger, stackTrace, cause);
          }
        }
      } catch (RuntimeException | IllegalAccessException e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        // Convert stack trace to string array
        respondIncident(externalTaskTrigger, stackTrace, e);
      }
    } else {
      // Worker has to respond itself by Responder or TaktXClient. Result is ignored
      try {
        method.invoke(beanInstance, arguments);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        switch (cause) {
          case TaktXBpmnError bpmnError -> respondError(externalTaskTrigger, bpmnError);
          case TaktXBpmnEscalation bpmnEscalation ->
              respondEscalation(externalTaskTrigger, bpmnEscalation);
          case TaktXBpmnPromise bpmnPromise -> respondPromis(externalTaskTrigger, bpmnPromise);
          default -> {
            StackTraceElement[] stackTrace = cause.getStackTrace();
            // Convert stack trace to string array
            respondIncident(externalTaskTrigger, stackTrace, cause);
          }
        }
      } catch (RuntimeException | IllegalAccessException e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        // Convert stack trace to string array
        respondIncident(externalTaskTrigger, stackTrace, e);
      }
    }
  }

  private void respondPromis(
      ExternalTaskTriggerDTO externalTaskTriggerDTO, TaktXBpmnPromise bpmnPromise) {
    externalTaskResponder
        .responderForExternalTaskTrigger(externalTaskTriggerDTO)
        .respondPromise(bpmnPromise.getDuration());
  }

  private void respondEscalation(
      ExternalTaskTriggerDTO externalTaskTriggerDTO, TaktXBpmnEscalation bpmnEscalation) {
    externalTaskResponder
        .responderForExternalTaskTrigger(externalTaskTriggerDTO)
        .respondEscalation(
            bpmnEscalation.getErrorCode(),
            bpmnEscalation.getErrorMessage(),
            bpmnEscalation.getVariables());
  }

  private void respondError(
      ExternalTaskTriggerDTO externalTaskTriggerDTO, TaktXBpmnError bpmnError) {
    externalTaskResponder
        .responderForExternalTaskTrigger(externalTaskTriggerDTO)
        .respondError(
            bpmnError.getAllowRetry(),
            bpmnError.getErrorCode(),
            bpmnError.getErrorMessage(),
            bpmnError.getVariables());
  }

  private void respondIncident(
      ExternalTaskTriggerDTO externalTaskTriggerDTO,
      StackTraceElement[] stackTrace,
      Throwable cause) {
    String[] stackTraceStrings =
        Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new);
    externalTaskResponder
        .responderForExternalTaskTrigger(externalTaskTriggerDTO)
        .respondIncident(cause.getMessage(), stackTraceStrings);
  }

  private Object processResult(
      Method method, Object result, ExternalTaskTriggerDTO externalTaskTrigger) {
    if (result != null) {
      ResultProcessor resultProcessor = resultProcessorFactory.create(method.getReturnType());
      if (resultProcessor != null) {
        return resultProcessor.process(result, externalTaskTrigger);
      }
    }
    return new HashMap<String, JsonNode>();
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
