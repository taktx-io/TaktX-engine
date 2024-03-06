package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ExternalTriggerConsumer {
  Deployer deployer;

  ObjectMapper objectMapper;

  public ExternalTriggerConsumer(Deployer deployer, ObjectMapper objectMapper) {
    this.deployer = deployer;
    this.objectMapper = objectMapper;
  }

  @Incoming("external-task-trigger-incoming")
  public void consume(ExternalTaskTrigger externalTaskTrigger)
      throws InvocationTargetException, IllegalAccessException {
    String processDefinitionId =
        externalTaskTrigger.getProcessDefinitionKey().getProcessDefinitionId();
    Integer generation = externalTaskTrigger.getProcessDefinitionKey().getGeneration();
    String externalTaskId = externalTaskTrigger.getExternalTaskId();
    Object workerInstance = deployer.getDefinitionMap().get(processDefinitionId).get(generation);

    // Get method from workerInstance which has matching annotation
    Class<?> aClass = workerInstance.getClass();
    Optional<Method> optMethod = findMatchingMethod(aClass, externalTaskId);
    if (optMethod.isPresent()) {
      Method method = optMethod.get();
      Object result = method.invoke(workerInstance, getParameters(method, externalTaskTrigger));
    } else {
      throw new IllegalStateException("No method found for external task " + externalTaskId);
    }
  }

  private Object[] getParameters(Method method, ExternalTaskTrigger externalTaskTrigger) {
    // This method has the matching annotation
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Map<String, JsonNode> variables = externalTaskTrigger.getVariables();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getType().equals(ExternalTaskTrigger.class)) {
        args[i] = externalTaskTrigger;
      } else {
        JsonNode jsonNode = variables.get(parameters[i].getName());
        // Convert jsonNode to the required type
        try {
          args[i] = objectMapper.convertValue(jsonNode, parameters[i].getType());
        } catch (IllegalArgumentException e) {
          // If the conversion fails, set the argument to null
          args[i] = null;
        }
      }
    }
    return args;
  }

  private Optional<Method> findMatchingMethod(Class<?> aClass, String externalTaskId) throws IllegalAccessException, InvocationTargetException {
    for (Method method : aClass.getDeclaredMethods()) {
      ExternalTask externalTaskAnnotation = method.getAnnotation(ExternalTask.class);
      if (externalTaskAnnotation != null
              && externalTaskAnnotation.element().equals(externalTaskId)) {
        return Optional.of(method);
      }
    }
    if (aClass.getSuperclass() != null) {
      return findMatchingMethod(aClass.getSuperclass(), externalTaskId);
    }
    return Optional.empty();
  }
}
