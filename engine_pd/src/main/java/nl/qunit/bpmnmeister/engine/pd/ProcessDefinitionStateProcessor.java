package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessDefinitionStateProcessor
    implements Processor<
        ProcessDefinitionKey, ProcessDefinition, ProcessDefinitionKey, ProcessActivation> {
  private ProcessorContext<ProcessDefinitionKey, ProcessActivation> context;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionStateWrapper> stateStore;

  @Override
  public void init(ProcessorContext context) {
    // Access the state store
    this.context = context;
    this.stateStore = this.context.getStateStore(Stores.PD_STATE_STORE_NAME);
  }

  @Override
  public void process(Record<ProcessDefinitionKey, ProcessDefinition> record) {

    if (stateStore.get(record.key()) == null) {
      // Deactivate all other versions and generations of this pd
      stateStore
          .all()
          .forEachRemaining(
              kv -> {
                if (kv.key.getProcessDefinitionId().equals(record.key().getProcessDefinitionId())
                    && kv.key.getGeneration().equals(record.key().getGeneration())) {
                  ProcessActivation processActivation =
                      ProcessActivation.builder()
                          .newState(ProcessDefinitionStateEnum.INACTIVE)
                          .build();
                  context.forward(new Record<>(kv.key, processActivation, record.timestamp()));
                }
              });

      // Store and activate the new version
      ProcessDefinitionStateWrapper processDefinitionState =
          ProcessDefinitionStateWrapper.builder()
              .processDefinition(record.value())
              .state(ProcessDefinitionStateEnum.INITIAL)
              .build();
      stateStore.put(record.key(), processDefinitionState);
      ProcessActivation processActivation =
          ProcessActivation.builder().newState(ProcessDefinitionStateEnum.ACTIVE).build();
      context.forward(new Record<>(record.key(), processActivation, record.timestamp()));
    } else {
      // Key already exists, should not happen
      System.out.println("Key already exists: " + record.key());
    }
  }
}
