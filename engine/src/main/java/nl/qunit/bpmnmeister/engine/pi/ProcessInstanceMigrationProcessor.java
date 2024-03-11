package nl.qunit.bpmnmeister.engine.pi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ElementStates;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceMigrationTrigger;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@RequiredArgsConstructor
public class ProcessInstanceMigrationProcessor
    implements Processor<ProcessInstanceKey, ProcessInstanceMigrationTrigger, Object, Object> {

  private final ProcessorProvider processorProvider;

  private KeyValueStore<ProcessInstanceKey, ProcessInstance> processInstanceStore;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
  }

  @Override
  public void process(Record<ProcessInstanceKey, ProcessInstanceMigrationTrigger> triggerRecord) {
    ProcessInstance processInstance = processInstanceStore.get(triggerRecord.key());
    ProcessDefinition oldProcessDefinition = processInstance.getProcessDefinition();
    ProcessDefinition newProcessDefinition = triggerRecord.value().getNewProcessDefinition();

    Set<BaseElementId> existingIds = oldProcessDefinition.getDefinitions().getElements().keySet();
    Set<BaseElementId> newDefinitionIds =
        newProcessDefinition.getDefinitions().getElements().keySet();

    Set<BaseElementId> updatedIds = new HashSet<>(existingIds);
    updatedIds.retainAll(newDefinitionIds);

    Set<BaseElementId> newIds = new HashSet<>(newDefinitionIds);
    newIds.removeAll(existingIds);

    Map<BaseElementId, BpmnElementState> newElementStates = new HashMap<>();
    updatedIds.forEach(
        updatedId -> {
          BaseElement oldElement =
              oldProcessDefinition.getDefinitions().getElements().get(updatedId);
          BaseElement newElement =
              newProcessDefinition.getDefinitions().getElements().get(updatedId);
          BpmnElementState oldState = processInstance.getElementStates().get(updatedId);
          if (oldElement.getClass().equals(newElement.getClass())) {
            newElementStates.put(updatedId, oldState);
          } else {
            BpmnElementState newElementState =
                processorProvider.getProcessor(newElement).initialState();
            newElementStates.put(updatedId, newElementState);
          }
        });

    newIds.forEach(
        newId -> {
          BaseElement newElement = newProcessDefinition.getDefinitions().getElements().get(newId);
          BpmnElementState newElementState =
              processorProvider.getProcessor(newElement).initialState();
          newElementStates.put(newId, newElementState);
        });

    ProcessInstance newProcessInstance =
        new ProcessInstance(
            processInstance.getParentProcessInstanceKey(),
            processInstance.getProcessInstanceKey(),
            newProcessDefinition,
            new ElementStates(newElementStates),
            processInstance.getVariables());

    processInstanceStore.put(triggerRecord.key(), newProcessInstance);
  }
}
