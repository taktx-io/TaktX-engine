package com.flomaestro.engine.pi.processor;

import static com.cronutils.utils.StringUtils.isNumeric;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.generic.KafkaClients;
import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pd.RepeatDuration;
import com.flomaestro.engine.pd.model.ExternalTask;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.ErrorEventSignal;
import com.flomaestro.engine.pi.model.EscalationEventSignal;
import com.flomaestro.engine.pi.model.ExternalTaskInfo;
import com.flomaestro.engine.pi.model.ExternalTaskInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.streams.state.KeyValueStore;

@NoArgsConstructor
@Setter
public abstract class ExternalTaskInstanceProcessor<
        E extends ExternalTask, I extends ExternalTaskInstance<E>>
    extends ActivityInstanceProcessor<E, I, ExternalTaskResponseTriggerDTO> {

  private long retentionMs;

  protected ExternalTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      KafkaClients kafkaClients) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.retentionMs =
        getRetentionForTopicConfiguration(
            kafkaClients,
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()));
  }

  public long getRetentionForTopicConfiguration(KafkaClients kafkaClients, String topicName) {
    try {
      // Specify the type of resource we want to describe (TOPIC in this case)
      ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

      // Call Kafka's Admin API to describe the topic configuration

      DescribeConfigsResult result =
          kafkaClients.getAdmin().describeConfigs(java.util.Collections.singleton(resource));

      // Extract the configuration details for the topic
      Config config = result.all().get().get(resource);
      config.get("retention.ms");
      // Extract each configuration entry and return it as a map
      return Long.parseLong(config.get("retention.ms").value());
    } catch (ExecutionException | InterruptedException e) {
      throw new IllegalStateException(
          "Failed to retrieve configuration for topic: " + topicName, e);
    }
  }

  @Override
  protected void processStartSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    ExternalTask flowNode = flownodeInstance.getFlowNode();
    String externalTaskId = getExternalTaskId(flowNode.getWorkerDefinition(), variables);

    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(externalTaskId, flowNode, flownodeInstance, variables, null);
    instanceResult.addExternalTaskRequest(externalTaskInfo);
    flownodeInstance.setState(ActtivityStateEnum.WAITING);
    flownodeInstance.setAttempt(0);

//    instanceResult.addScheduledExternalTaskTriggerTimeout(
//        new ScheduledExternalTaskTriggerTimeoutInfo(flownodeInstance, retentionMs));
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {

    ExternalTaskResponseResultDTO responseResult = trigger.getExternalTaskResponseResult();

    if (ExternalTaskResponseType.SUCCESS == responseResult.getResponseType()) {
      handleSuccess(instanceResult, externalTaskInstance);
    } else if (ExternalTaskResponseType.PROMISE == responseResult.getResponseType()) {
      handlePromise(instanceResult, externalTaskInstance, trigger);
    } else if (ExternalTaskResponseType.ESCALATION == responseResult.getResponseType()) {
      handleEscalation(instanceResult, directInstanceResult, externalTaskInstance, responseResult);
    } else if (ExternalTaskResponseType.TIMEOUT == responseResult.getResponseType()
        || ExternalTaskResponseType.ERROR == responseResult.getResponseType()) {
      handleErrorOrTimeout(
          instanceResult, directInstanceResult, externalTaskInstance, variables, responseResult);
    }
  }

  private void handleErrorOrTimeout(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      VariableScope flowNodeInstanceVariables,
      ExternalTaskResponseResultDTO responseResult) {
    E externalTask = externalTaskInstance.getFlowNode();
    if (externalTask.getRetries() != null) {
      // We have some kind of retry definition
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(
              externalTask.getRetries(), flowNodeInstanceVariables);
      String retryString = jsonNode.asText();

      // Analyze the retry definition
      int retries = -1;
      Optional<Duration> backoff = Optional.empty();
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
            getExternalTaskId(externalTask.getWorkerDefinition(), flowNodeInstanceVariables);

        ioMappingProcessor.addInputVariables(externalTask, flowNodeInstanceVariables);
        if (backoff.isPresent()) {
          // This means for now we do nothing, the retry will be scheduled by the scheduler
          scheduleNextExternalTask(
              externalTaskId,
              backoff.get(),
              externalTask,
              externalTaskInstance,
              flowNodeInstanceVariables,
              instanceResult);
        } else {
          // No backoff time defined, retry directly
          ExternalTaskInfo externalTaskInfo =
              getExternalTaskInfo(
                  externalTaskId,
                  externalTask,
                  externalTaskInstance,
                  flowNodeInstanceVariables,
                  null);
          instanceResult.addExternalTaskRequest(externalTaskInfo);
        }
      } else {
        // No more retries, either by limit or by disallowing retry by the worker
        directInstanceResult.addEvent(
            new ErrorEventSignal(
                externalTaskInstance,
                responseResult.getName(),
                responseResult.getCode(),
                responseResult.getMessage()));
      }
    } else {
      // No retries allowed
      cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
      directInstanceResult.addEvent(
          new ErrorEventSignal(
              externalTaskInstance,
              responseResult.getName(),
              responseResult.getCode(),
              responseResult.getMessage()));
    }
  }

  private void handleEscalation(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I externalTaskInstance,
      ExternalTaskResponseResultDTO responseResult) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    directInstanceResult.addEvent(
        new EscalationEventSignal(
            externalTaskInstance,
            responseResult.getName(),
            responseResult.getCode(),
            responseResult.getMessage()));
  }

  private void handlePromise(
      InstanceResult instanceResult,
      I externalTaskInstance,
      ExternalTaskResponseTriggerDTO trigger) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    instanceResult.addScheduledExternalTaskTriggerTimeout(
        new ScheduledExternalTaskTriggerTimeoutInfo(
            externalTaskInstance, trigger.getExternalTaskResponseResult().getTimeout()));
  }

  private void handleSuccess(InstanceResult instanceResult, I externalTaskInstance) {
    cancelTimeoutScheduledTrigger(instanceResult, externalTaskInstance);
    externalTaskInstance.setState(ActtivityStateEnum.FINISHED);
  }

  private void cancelTimeoutScheduledTrigger(
      InstanceResult instanceResult, I externalTaskInstance) {
    externalTaskInstance.getScheduledKeys().forEach(instanceResult::cancelSchedule);
    externalTaskInstance.clearScheduledKeys();
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    // Nothing to do here
  }

  private String getExternalTaskId(String workerDefinition, VariableScope variables) {
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression(workerDefinition, variables);
    return jsonNode.asText();
  }

  private void scheduleNextExternalTask(
      String workerDefinition,
      Duration backoff,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      InstanceResult instanceResult) {
    String triggerTime = Instant.now(clock).plus(backoff).toString();
    ExternalTaskInfo externalTaskInfo =
        getExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);

    instanceResult.addExternalTaskRequest(externalTaskInfo);
  }

  private static ExternalTaskInfo getExternalTaskInfo(
      String workerDefinition,
      ExternalTask externalTask,
      ExternalTaskInstance<?> instance,
      VariableScope variables,
      String triggerTime) {
    return new ExternalTaskInfo(workerDefinition, externalTask, instance, variables, triggerTime);
  }
}
