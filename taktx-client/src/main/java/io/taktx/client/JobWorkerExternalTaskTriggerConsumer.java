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
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Slf4j
public class JobWorkerExternalTaskTriggerConsumer
    implements Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> {

  private final TaktClient taktClient;
  private final Object beanInstance;
  private final Map<String, Method> workerMethods = new HashMap<>();

  public JobWorkerExternalTaskTriggerConsumer(TaktClient taktClient, Object beanInstance) {
    this.taktClient = taktClient;
    this.beanInstance = beanInstance;

    scanClass(beanInstance.getClass());
  }

  private void scanClass(Class<?> clazz) {
    for (Method declaredMethod : clazz.getDeclaredMethods()) {
      if (declaredMethod.getAnnotation(TaktWorkerMethod.class) != null) {
        log.info("Registering worker method {}", declaredMethod.getName());
        workerMethods.put(
            declaredMethod.getAnnotation(TaktWorkerMethod.class).taskId(), declaredMethod);
      }
    }
    if (clazz.getSuperclass() != null) {
      scanClass(clazz.getSuperclass());
    }
  }

  @Override
  public void accept(ConsumerRecord<UUID, ExternalTaskTriggerDTO> externalTaskTriggerRecord) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO = externalTaskTriggerRecord.value();
    Method method = workerMethods.get(externalTaskTriggerDTO.getExternalTaskId());
    if (method == null) {
      log.error(
          "No worker method found for task id: {}", externalTaskTriggerDTO.getExternalTaskId());
      taktClient
          .respondToExternalTask(externalTaskTriggerDTO)
          .respondError(
              false,
              "TASK_NOT_FOUND",
              "Worker method not found",
              "No worker method found for task id " + externalTaskTriggerDTO.getExternalTaskId());
      return;
    }

    TaktWorkerMethod workerMethod = method.getAnnotation(TaktWorkerMethod.class);
    boolean autoComplete = workerMethod.autoComplete();

    Object[] arguments = resolveParameters(method, externalTaskTriggerDTO);
    boolean methodIsVoid = method.getReturnType().equals(Void.TYPE);

    if (autoComplete) {
      // Rely on result and exceptions to determine success or failure

      try {
        Object result = method.invoke(beanInstance, arguments);
        if (methodIsVoid) {
          taktClient.respondToExternalTask(externalTaskTriggerDTO).respondSuccess();
        } else {
          taktClient.respondToExternalTask(externalTaskTriggerDTO).respondSuccess(result);
        }
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        taktClient
            .respondToExternalTask(externalTaskTriggerDTO)
            .respondError(false, "ERROR", "Error", e.getMessage());
      }
    } else {
      // Worker has to respond itself by Responder or TaktClient. Result is ignored
      try {
        method.invoke(beanInstance, arguments);
      } catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
        taktClient
            .respondToExternalTask(externalTaskTriggerDTO)
            .respondError(false, "ERROR", "Error", e.getMessage());
      }
    }
  }

  private Object[] resolveParameters(Method method, ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    List<Object> result = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      TaktParameterResolverFactory parameterResolverFactory =
          taktClient.getParameterResolverFactory();
      TaktParameterResolver parameterResolver =
          parameterResolverFactory.create(taktClient, parameter);
      Object resolved = parameterResolver.resolve(externalTaskTriggerDTO);
      result.add(resolved);
    }

    return result.toArray();
  }
}
