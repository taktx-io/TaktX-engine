package nl.qunit.bpmnmeister.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ExternalTask2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ExternalTaskInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;

@NoArgsConstructor
public abstract class ExternalTaskInstanceProcessor<
        S extends ExternalTask2, I extends ExternalTaskInstance>
    extends ActivityInstanceProcessor<S, I, ExternalTaskResponseTrigger2> {

  private FeelExpressionHandler feelExpressionHandler;
  private Clock clock;
  private IoMappingProcessor ioMappingProcessor;

  protected ExternalTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor) {
    this.feelExpressionHandler = feelExpressionHandler;
    this.clock = clock;
    this.ioMappingProcessor = ioMappingProcessor;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, S flowNode, I flownodeInstance) {
    InstanceResult instanceResult = InstanceResult.empty();
    ExternalTaskInfo externalTaskInfo =
        ExternalTaskInfo.builder()
            .externalTaskId(flowNode.getWorkerDefinition())
            .elementId(flowNode.getId())
            .elementInstanceId(flownodeInstance.getElementInstanceId())
            .build();
    instanceResult.addExternalTaskRequest(externalTaskInfo);
    flownodeInstance.setState(FlowNodeStateEnum.WAITING);
    flownodeInstance.setAttempt(0);
    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      FlowElements2 flowElements,
      S externalTask,
      I externalTaskInstance,
      ExternalTaskResponseTrigger2 trigger,
      Variables2 variables) {
    InstanceResult instanceResult = InstanceResult.empty();
    if (Boolean.TRUE.equals(trigger.getExternalTaskResponseResult().getSuccess())) {
      processSucessfulResponse(flowElements, externalTask, externalTaskInstance, instanceResult);
    } else {
      if (!externalTask.getRetries().equals(Constants.NONE)) {
        // We have some kind of retry definition
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(externalTask.getRetries(), variables);
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
          if (backoff.isPresent()) {
            // This means for now we do nothing, the retry will be scheduled by the scheduler
            scheduleNextExternalTask(
                trigger.getElementId(),
                backoff.get(),
                externalTask.getId(),
                externalTaskInstance.getElementInstanceId(),
                variables,
                instanceResult);
          } else {
            // No backoff time defined, retry directly
            String externalTaskId =
                getExternalTaskId(externalTask.getWorkerDefinition(), variables);

            ExternalTaskInfo externalTaskInfo =
                ExternalTaskInfo.builder()
                    .externalTaskId(externalTaskId)
                    .elementId(trigger.getElementId())
                    .elementInstanceId(externalTaskInstance.getElementInstanceId())
                    .variables(getExternalTaskVariables(externalTask, variables))
                    .build();
            instanceResult.addExternalTaskRequest(externalTaskInfo);
          }
        } else {
          // No more retries, either by limit or by disallowing retry by the worker
          // fail the task and the processInstance
          externalTaskInstance.setState(FlowNodeStateEnum.TERMINATED);
        }
      } else {
        // No retries allowed, fail the task and the processInstance
        externalTaskInstance.setState(FlowNodeStateEnum.TERMINATED);
      }
    }
    return instanceResult;
  }

  private Variables2 getExternalTaskVariables(WithIoMapping element, Variables2 variables) {
    return ioMappingProcessor.getInputVariables(element, variables);
  }

  private String getExternalTaskId(String workerDefinition, Variables2 variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private void processSucessfulResponse(
      FlowElements2 flowElements,
      ExternalTask2 externalTask,
      ExternalTaskInstance flownodeInstance,
      InstanceResult instanceResult) {
    flownodeInstance.setState(FlowNodeStateEnum.FINISHED);
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      String backoff,
      String elementId,
      UUID elementInstanceId,
      Variables2 variables,
      InstanceResult instanceResult) {
    String triggerTime = Instant.now(clock).plus(Duration.parse(backoff)).toString();
    ExternalTaskInfo externalTaskInfo =
        ExternalTaskInfo.builder()
            .externalTaskId(workerDefinition)
            .elementId(elementId)
            .elementInstanceId(elementInstanceId)
            .variables(variables)
            .startTime(triggerTime)
            .build();

    instanceResult.addExternalTaskRequest(externalTaskInfo);
  }
}
