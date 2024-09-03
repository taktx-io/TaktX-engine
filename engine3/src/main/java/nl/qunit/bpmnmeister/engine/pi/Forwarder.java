package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
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
              ExternalTaskTrigger newExternalTaskTrigger =
                  toTrigger(externalTask, processInstance.getProcessInstanceKey(), definitionKey);
              if (externalTask.startTime() == null) {
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
                        externalTask.element().getId(),
                        externalTask.element().getId(),
                        List.of(newExternalTaskTrigger),
                        externalTask.startTime());
                ScheduleKey scheduleKey =
                    new ScheduleKey(
                        definitionKey,
                        processInstance.getProcessInstanceKey(),
                        oneTimeScheduler.getScheduleType(),
                        externalTask.element().getId(),
                        "");
                context.forward(
                    new Record<>(scheduleKey, oneTimeScheduler, Instant.now().toEpochMilli()));
              }
            });
  }

  private ExternalTaskTrigger toTrigger(
      ExternalTaskInfo externalTaskInfo,
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey) {
    return new ExternalTaskTrigger(
        processInstanceKey,
        processDefinitionKey,
        getElementIdPath(externalTaskInfo),
        externalTaskInfo.externalTaskId(),
        getInstancePath(externalTaskInfo),
        variablesMapper.toDTO(externalTaskInfo.variables()));
  }

  private List<UUID> getInstancePath(ExternalTaskInfo externalTaskInfo) {
    List<UUID> instancePath = new ArrayList<>();
    instancePath.add(externalTaskInfo.instance().getElementInstanceId());

    FLowNodeInstance parent = externalTaskInfo.instance().getParentInstance();
    while (parent != null) {
      instancePath.add(parent.getElementInstanceId());
      parent = parent.getParentInstance();
    }
    Collections.reverse(instancePath);
    return instancePath;
  }

  private static List<String> getElementIdPath(ExternalTaskInfo externalTaskInfo) {
    // Create a list of parent element IDs recursively from the element's parent, the order of the
    // list is from the root to the parent of the element
    List<String> elementIdPath = new ArrayList<>();
    elementIdPath.add(externalTaskInfo.element().getId());
    FlowElement2 parent = externalTaskInfo.element().getParentElement();
    while (parent != null) {
      elementIdPath.add(parent.getId());
      parent = parent.getParentElement();
    }
    Collections.reverse(elementIdPath);
    return elementIdPath;
  }
}
