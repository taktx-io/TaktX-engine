package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance2;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@ApplicationScoped
@RequiredArgsConstructor
public class Forwarder {
  private final VariablesMapper variablesMapper;

  public void forward(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstance2 processInstance) {

    forwardExternalTaskRequests(context, instanceResult, definitionKey, processInstance);
  }

  private void forwardExternalTaskRequests(
      ProcessorContext<Object, Object> context,
      InstanceResult instanceResult,
      ProcessDefinitionKey definitionKey,
      ProcessInstance2 processInstance) {
    instanceResult
        .getExternalTaskRequests()
        .forEach(
            externalTask -> {
              VariablesDTO variablesDTO = variablesMapper.toDTO(externalTask.getVariables());
              ExternalTaskTrigger newExternalTaskTrigger =
                  new ExternalTaskTrigger(
                      processInstance.getProcessInstanceKey(),
                      definitionKey,
                      externalTask.getExternalTaskId(),
                      externalTask.getElementId(),
                      externalTask.getElementInstanceId(),
                      variablesDTO);
              if (externalTask.getStartTime() == null) {
                // No schedule time, forward directly
                context.forward(
                    new Record<>(
                        newExternalTaskTrigger.getProcessInstanceKey(),
                        newExternalTaskTrigger,
                        Instant.now().toEpochMilli()));
              } else {
                // Schedule the external task
                OneTimeScheduler oneTimeScheduler =
                    new OneTimeScheduler(
                        processInstance.getProcessDefinitionKey(),
                        processInstance.getProcessInstanceKey(),
                        externalTask.getElementId(),
                        externalTask.getElementId(),
                        List.of(newExternalTaskTrigger),
                        externalTask.getStartTime());
                ScheduleKey scheduleKey =
                    new ScheduleKey(
                        definitionKey,
                        processInstance.getProcessInstanceKey(),
                        oneTimeScheduler.getScheduleType(),
                        externalTask.getElementId(),
                        "");
                context.forward(
                    new Record<>(scheduleKey, oneTimeScheduler, Instant.now().toEpochMilli()));
              }
            });
  }
}
