package nl.qunit.bpmnmeister.engine.pi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.FlowNodeStates;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceMigrationTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@RequiredArgsConstructor
public class ProcessInstanceMigrationProcessor
    implements Processor<UUID, ProcessInstanceMigrationTrigger, Object, Object> {

  private final ProcessorProvider processorProvider;

  private KeyValueStore<UUID, ProcessInstance> processInstanceStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processInstanceDefinitionStore;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
    this.processInstanceDefinitionStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceMigrationTrigger> triggerRecord) {
    ProcessInstance processInstance = processInstanceStore.get(triggerRecord.key());
    ProcessDefinition oldProcessDefinition =
        processInstanceDefinitionStore.get(processInstance.getProcessDefinitionKey());
    ProcessDefinition newProcessDefinition = triggerRecord.value().getNewProcessDefinition();

    Set<String> existingIds =
        oldProcessDefinition.getDefinitions().getRootProcess().getFlowElements().keySet();
    Set<String> newDefinitionIds =
        newProcessDefinition.getDefinitions().getRootProcess().getFlowElements().keySet();

    Set<String> updatedIds = new HashSet<>(existingIds);
    updatedIds.retainAll(newDefinitionIds);

    Set<String> newIds = new HashSet<>(newDefinitionIds);
    newIds.removeAll(existingIds);

    Map<String, FlowNodeState> newElementStates = new HashMap<>();
    updatedIds.forEach(
        updatedId -> {
          BaseElement oldElement =
              oldProcessDefinition
                  .getDefinitions()
                  .getRootProcess()
                  .getFlowElements()
                  .get(updatedId);
          BaseElement newElement =
              newProcessDefinition
                  .getDefinitions()
                  .getRootProcess()
                  .getFlowElements()
                  .get(updatedId);
          Optional<FlowNodeState> oldState = processInstance.getFlowNodeStates().get(updatedId);
          if (oldState.isPresent() && oldElement.getClass().equals(newElement.getClass())) {
            newElementStates.put(updatedId, oldState.get());
          }
        });

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(newProcessDefinition);
    ProcessInstance newProcessInstance =
        new ProcessInstance(
            processInstance.getRootInstanceKey(),
            processInstance.getProcessInstanceKey(),
            processInstance.getParentInstanceKey(),
            processInstance.getParentElementId(),
            processDefinitionKey,
            new FlowNodeStates(newElementStates),
            processInstance.getProcessInstanceState());

    processInstanceStore.put(triggerRecord.key(), newProcessInstance);
    processInstanceDefinitionStore.put(processDefinitionKey, newProcessDefinition);
  }
}
