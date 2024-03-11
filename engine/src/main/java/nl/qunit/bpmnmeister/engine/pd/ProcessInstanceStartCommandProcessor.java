package nl.qunit.bpmnmeister.engine.pd;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceStartCommandProcessor
    implements Processor<
        ProcessDefinitionKey,
        ProcessInstanceStartCommand,
        ProcessInstanceKey,
        ProcessInstanceTrigger> {
  private ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionActivation>
      processDefintionActivationStore;

  @Override
  public void init(ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context) {
    this.context = context;
    this.processDefintionActivationStore =
        context.getStateStore(Stores.PROCESS_DEFINITION_ACTIVATION_STORE_NAME);
  }

  @Override
  public void process(
      Record<ProcessDefinitionKey, ProcessInstanceStartCommand> startCommandRecord) {
    ProcessDefinitionActivation processDefinitionActivation =
        this.processDefintionActivationStore.get(
            startCommandRecord.value().getProcessDefinitionKey());
    if (processDefinitionActivation == null) {
      throw new IllegalStateException(
          "Process definition activation not found for key: " + startCommandRecord.key());
    }
    if (processDefinitionActivation.getState() == ProcessDefinitionStateEnum.ACTIVE) {
      ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
      ProcessInstanceTrigger processInstanceTrigger =
          new ProcessInstanceTrigger(
              processInstanceKey,
              ProcessInstanceKey.NONE,
              processDefinitionActivation.getProcessDefinition(),
              startCommandRecord.value().getElementId(),
              false,
              BaseElementId.NONE,
              startCommandRecord.value().getVariables());
      context.forward(
          new Record<>(processInstanceKey, processInstanceTrigger, startCommandRecord.timestamp()));
    } else {
      throw new IllegalStateException(
          "Process definition is not active: " + startCommandRecord.key());
    }
  }
}
