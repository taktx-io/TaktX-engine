package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessActivation;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessActivationProcessor
    implements Processor<
        ProcessDefinitionKey, ProcessDefinition, ProcessDefinitionKey, ProcessActivation> {
  private ProcessorContext<ProcessDefinitionKey, ProcessActivation> context;

  @Override
  public void init(ProcessorContext<ProcessDefinitionKey, ProcessActivation> context) {
    this.context = context;
  }

  @Override
  public void process(Record<ProcessDefinitionKey, ProcessDefinition> incoming) {
    ProcessDefinitionKey key = incoming.key();
    ProcessDefinition processDefinition = incoming.value();
    if (key.getVersion() > 1) {
      ProcessDefinitionKey previousVersionKey =
          ProcessDefinitionKey.builder()
              .processDefinitionId(key.getProcessDefinitionId())
              .generation(key.getGeneration())
              .version(key.getVersion() - 1)
              .build();
      ProcessActivation deActivation =
          ProcessActivation.builder().state(ProcessDefinitionStateEnum.INACTIVE).build();
      context.forward(new Record<>(previousVersionKey, deActivation, incoming.timestamp()));
    }
    ProcessActivation activation =
        ProcessActivation.builder()
            .processDefinition(processDefinition)
            .state(ProcessDefinitionStateEnum.ACTIVE)
            .build();
    context.forward(new Record<>(key, activation, incoming.timestamp()));
  }
}
