package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExternalTriggerConsumer {
  private static final Logger LOG = Logger.getLogger(ExternalTriggerConsumer.class);

  Deployer deployer;

  ObjectMapper objectMapper;

  @Inject
  @Channel("external-task-response-outgoing")
  Emitter<ExternalTaskResponseTrigger> externalTaskResponseResultEmitter;

  public ExternalTriggerConsumer(Deployer deployer, ObjectMapper objectMapper) {
    this.deployer = deployer;
    this.objectMapper = objectMapper;
  }

  @Incoming("external-task-trigger-incoming")
  public void consume(ExternalTaskTrigger externalTaskTrigger)
      throws InvocationTargetException, IllegalAccessException {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    BaseElementId processDefinitionId =
        externalTaskTrigger.getProcessDefinitionKey().getProcessDefinitionId();
    BaseElementId externalTaskId = externalTaskTrigger.getElementId();
    Object workerInstance = deployer.getDefinitionMap().get(processDefinitionId);

    // Get method from workerInstance which has matching annotation
    Class<?> aClass = workerInstance.getClass();
    Optional<Method> optMethod = findMatchingMethod(aClass, externalTaskId);
    if (optMethod.isPresent()) {
      Method method = optMethod.get();
      Object result = method.invoke(workerInstance, getParameters(method, externalTaskTrigger));
      Map<String, JsonNode> variablesMap =
          result == null ? Map.of() : objectMapper.convertValue(result, LinkedHashMap.class);

      ExternalTaskResponseResult externalTaskResponseResult =
          new ExternalTaskResponseResult(true, null);
      ExternalTaskResponseTrigger processInstanceTrigger =
          new ExternalTaskResponseTrigger(
              externalTaskTrigger.getProcessInstanceKey(),
              externalTaskId,
              externalTaskResponseResult,
              new Variables(variablesMap));
      LOG.info("Returning process instance trigger: " + processInstanceTrigger);
      externalTaskResponseResultEmitter.send(
          KafkaRecord.of(externalTaskTrigger.getProcessInstanceKey(), processInstanceTrigger));

    } else {
      throw new IllegalStateException("No method found for external task " + externalTaskId);
    }
  }

  private Object[] getParameters(Method method, ExternalTaskTrigger externalTaskTrigger) {
    // This method has the matching annotation
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Variables variables = externalTaskTrigger.getVariables();
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

  private Optional<Method> findMatchingMethod(Class<?> aClass, BaseElementId externalTaskId) {
    for (Method method : aClass.getDeclaredMethods()) {
      ExternalTask externalTaskAnnotation = method.getAnnotation(ExternalTask.class);
      if (externalTaskAnnotation != null
          && externalTaskAnnotation.element().equals(externalTaskId.getId())) {
        return Optional.of(method);
      }
    }
    if (aClass.getSuperclass() != null) {
      return findMatchingMethod(aClass.getSuperclass(), externalTaskId);
    }
    return Optional.empty();
  }
}
