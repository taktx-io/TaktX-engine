package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.ExternalTask;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTypeEnum;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.instances.ErrorEventSignal;
import nl.qunit.bpmnmeister.pi.instances.EscalationEventSignal;
import nl.qunit.bpmnmeister.pi.instances.ExternalTaskInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

@NoArgsConstructor
@Setter
public abstract class ExternalTaskInstanceProcessor<
        E extends ExternalTask, I extends ExternalTaskInstance<E>>
    extends ActivityInstanceProcessor<E, I, ExternalTaskResponseTrigger> {

  private FeelExpressionHandler feelExpressionHandler;
  private Clock clock;
  private VariablesMapper variablesMapper;

  protected ExternalTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
    this.feelExpressionHandler = feelExpressionHandler;
    this.clock = clock;
    this.variablesMapper = variablesMapper;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements flowElements, I flownodeInstance, String inputFlowId, Variables variables) {
    InstanceResult instanceResult = InstanceResult.empty();
    ExternalTask flowNode = flownodeInstance.getFlowNode();
    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(
            flowNode.getWorkerDefinition(), flowNode, flownodeInstance, variables, null);
    instanceResult.addExternalTaskRequest(externalTaskInfo);
    flownodeInstance.setState(ActtivityStateEnum.WAITING);
    flownodeInstance.setAttempt(0);
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements flowElements,
      I externalTaskInstance,
      ExternalTaskResponseTrigger trigger,
      Variables processInstanceVariables) {
    VariablesDTO variablesDTO = trigger.getVariables();
    Variables variables = variablesMapper.fromDTO(variablesDTO);
    processInstanceVariables.merge(variables);

    InstanceResult instanceResult = InstanceResult.empty();
    ExternalTaskResponseResult responseResult = trigger.getExternalTaskResponseResult();
    if (ExternalTaskResponseTypeEnum.SUCCESS == responseResult.getResponseType()) {
      externalTaskInstance.setState(ActtivityStateEnum.FINISHED);
    } else if (ExternalTaskResponseTypeEnum.ESCALATION == responseResult.getResponseType()) {
      instanceResult.addEvent(
          new EscalationEventSignal(
              externalTaskInstance,
              responseResult.getName(),
              responseResult.getCode(),
              responseResult.getMessage()));
    } else if (ExternalTaskResponseTypeEnum.ERROR == responseResult.getResponseType()) {
      E externalTask = externalTaskInstance.getFlowNode();
      if (externalTask.getRetries() != null) {
        // We have some kind of retry definition
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(
                externalTask.getRetries(), processInstanceVariables);
        String retryString = jsonNode.asText();

        // Analyze the retry definition
        int retries = -1;
        Optional<String> backoff = Optional.empty();
        if (isNumeric(retryString)) {
          // Definition is just a number
          retries = Integer.parseInt(retryString);
        } else {
          // Definition might be a repeat limit with a backoff time
          try {
            RepeatDuration repeatDuration = RepeatDuration.parse(retryString);
            retries = repeatDuration.getRepetitions();
            backoff = Optional.ofNullable(repeatDuration.getDuration());
          } catch (DateTimeParseException e) {
            // Definition is not a valid repeat duration, since retries is still set
            // to -1 it will fail the task and the process instance
          }
        }

        if (externalTaskInstance.increaseAttempt() <= retries
            && Boolean.TRUE.equals(responseResult.getAllowRetry())) {
          // Retry allowed, possibly with backoff
          String externalTaskId =
              getExternalTaskId(externalTask.getWorkerDefinition(), processInstanceVariables);

          if (backoff.isPresent()) {
            // This means for now we do nothing, the retry will be scheduled by the scheduler
            scheduleNextExternalTask(
                externalTaskId,
                backoff.get(),
                externalTask,
                externalTaskInstance,
                ioMappingProcessor.getInputVariables(externalTask, processInstanceVariables),
                instanceResult);
          } else {
            // No backoff time defined, retry directly
            ExternalTaskInfo externalTaskInfo =
                getExternalTaskInfo(
                    externalTaskId,
                    externalTask,
                    externalTaskInstance,
                    ioMappingProcessor.getInputVariables(externalTask, processInstanceVariables),
                    null);
            instanceResult.addExternalTaskRequest(externalTaskInfo);
          }
        } else {
          // No more retries, either by limit or by disallowing retry by the worker
          instanceResult.addEvent(
              new ErrorEventSignal(
                  externalTaskInstance,
                  responseResult.getName(),
                  responseResult.getCode(),
                  responseResult.getMessage()));
        }
      } else {
        // No retries allowed
        instanceResult.addEvent(
            new ErrorEventSignal(
                externalTaskInstance,
                responseResult.getName(),
                responseResult.getCode(),
                responseResult.getMessage()));
      }
    }
    return instanceResult;
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(
      I instance, Variables variables) {
    // Nothing to do here
    return InstanceResult.empty();
  }

  private String getExternalTaskId(String workerDefinition, Variables variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      Variables variables,
      InstanceResult instanceResult) {
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);

    instanceResult.addExternalTaskRequest(externalTaskInfo);
  }

  private static ExternalTaskInfo getExternalTaskInfo(
      String workerDefinition,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      Variables variables,
      String triggerTime) {
    return new ExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);
  }
}
