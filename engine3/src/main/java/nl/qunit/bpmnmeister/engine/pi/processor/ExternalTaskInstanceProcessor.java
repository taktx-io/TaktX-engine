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
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ExternalTask2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.instances.ExternalTaskInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

@NoArgsConstructor
@Setter
public abstract class ExternalTaskInstanceProcessor<
        E extends ExternalTask2, I extends ExternalTaskInstance, S extends StartFlowElementTrigger>
    extends ActivityInstanceProcessor<E, I, ExternalTaskResponseTrigger2> {

  private FeelExpressionHandler feelExpressionHandler;
  private Clock clock;
  private VariablesMapper variablesMapper;

  protected ExternalTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor);
    this.feelExpressionHandler = feelExpressionHandler;
    this.clock = clock;
    this.variablesMapper = variablesMapper;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      E flowNode,
      I flownodeInstance,
      Variables2 processInstanceVariables) {
    InstanceResult instanceResult = InstanceResult.empty();
    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(
            flowNode.getWorkerDefinition(),
            flowNode,
            flownodeInstance,
            getExternalTaskVariables(flowNode, processInstanceVariables),
            null);
    instanceResult.addExternalTaskRequest(externalTaskInfo);
    flownodeInstance.setState(FlowNodeStateEnum.WAITING);
    flownodeInstance.setAttempt(0);
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      E externalTask,
      I externalTaskInstance,
      ExternalTaskResponseTrigger2 trigger,
      Variables2 processInstanceVariables) {
    VariablesDTO variablesDTO = trigger.getVariables();
    Variables2 variables = variablesMapper.fromDTO(variablesDTO);
    processInstanceVariables.merge(variables);

    InstanceResult instanceResult = InstanceResult.empty();
    if (Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getSuccess())) {
      processSucessfulResponse(
          flowElements,
          externalTask,
          externalTaskInstance,
          instanceResult,
          trigger,
          processInstanceVariables);
    } else {
      if (!externalTask.getRetries().equals(Constants.NONE)) {
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
            && Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getAllowRetry())) {
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
                getExternalTaskVariables(externalTask, processInstanceVariables),
                instanceResult);
          } else {
            // No backoff time defined, retry directly
            ExternalTaskInfo externalTaskInfo =
                getExternalTaskInfo(
                    externalTaskId,
                    externalTask,
                    externalTaskInstance,
                    getExternalTaskVariables(externalTask, processInstanceVariables),
                    null);
            instanceResult.addExternalTaskRequest(externalTaskInfo);
          }
        } else {
          // No more retries, either by limit or by disallowing retry by the worker
          // fail the task
          externalTaskInstance.setState(FlowNodeStateEnum.FAILED);
        }
      } else {
        // No retries allowed, fail the task
        externalTaskInstance.setState(FlowNodeStateEnum.FAILED);
      }
    }
    return instanceResult;
  }

  private Variables2 getExternalTaskVariables(WithIoMapping element, Variables2 variables) {
    // TODO When no mapping is defined, we should return the variables as is
    return ioMappingProcessor.getInputVariables(element, variables);
  }

  private String getExternalTaskId(String workerDefinition, Variables2 variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private void processSucessfulResponse(
      FlowElements2 flowElements,
      E externalTask,
      I externalTaskInstance,
      InstanceResult instanceResult,
      ExternalTaskResponseTrigger2 trigger,
      Variables2 processInstanceVariables) {
    externalTaskInstance.setState(FlowNodeStateEnum.FINISHED);
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      ExternalTask2 externalTask,
      ExternalTaskInstance instance,
      Variables2 variables,
      InstanceResult instanceResult) {
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);

    instanceResult.addExternalTaskRequest(externalTaskInfo);
  }

  private static ExternalTaskInfo getExternalTaskInfo(
      String workerDefinition,
      ExternalTask2 externalTask,
      ExternalTaskInstance instance,
      Variables2 variables,
      String triggerTime) {
    return new ExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);
  }
}
