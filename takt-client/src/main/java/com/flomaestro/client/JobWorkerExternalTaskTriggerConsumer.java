package com.flomaestro.client;

import com.flomaestro.client.annotation.TaktWorkerMethod;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobWorkerExternalTaskTriggerConsumer implements BiConsumer<UUID, ExternalTaskTriggerDTO> {

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
        workerMethods.put(declaredMethod.getAnnotation(TaktWorkerMethod.class).taskId(), declaredMethod);
      }
    }
    if (clazz.getSuperclass() != null) {
      scanClass(clazz.getSuperclass());
    }
  }

  @Override
  public void accept(UUID uuid, ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    Method method = workerMethods.get(externalTaskTriggerDTO.getExternalTaskId());
    if (method == null) {
      log.error("No worker method found for task id: {}", externalTaskTriggerDTO.getExternalTaskId());
      taktClient.respondToExternalTask(externalTaskTriggerDTO).respondError(
          false,
          "TASK_NOT_FOUND",
          "Worker method not found",
          "No worker method found for task id " + externalTaskTriggerDTO.getExternalTaskId());
      return;
    }

    Object[] arguments = resolveParameters(method, externalTaskTriggerDTO);
    try {
      method.invoke(beanInstance, arguments);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object[] resolveParameters(Method method, ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    List<Object> result = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      TaktParameterResolver parameterResolver = taktClient.getParameterResolver(parameter);
      Object resolved = parameterResolver.resolve(externalTaskTriggerDTO);
      result.add(resolved);
    }

    return result.toArray();
  }
}
