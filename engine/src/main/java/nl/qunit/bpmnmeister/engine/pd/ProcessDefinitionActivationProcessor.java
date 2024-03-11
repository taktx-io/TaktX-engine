package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessDefinitionActivationProcessor
    implements Processor<
        ProcessDefinitionKey,
        ProcessDefinition,
        ProcessDefinitionKey,
        ProcessDefinitionActivation> {
  private ProcessorContext<ProcessDefinitionKey, ProcessDefinitionActivation> context;

  @Override
  public void init(ProcessorContext<ProcessDefinitionKey, ProcessDefinitionActivation> context) {
    this.context = context;
  }

  @Override
  public void process(Record<ProcessDefinitionKey, ProcessDefinition> incoming) {
    ProcessDefinitionKey key = incoming.key();
    ProcessDefinition processDefinition = incoming.value();
    if (key.getVersion() > 1) {
      ProcessDefinitionKey previousVersionKey =
          new ProcessDefinitionKey(
              key.getProcessDefinitionId(),
              key.getGeneration(),
              key.getVersion() - 1);
      ProcessDefinitionActivation deActivation =
          new ProcessDefinitionActivation(ProcessDefinition.NONE, ProcessDefinitionStateEnum.INACTIVE);
      context.forward(new Record<>(previousVersionKey, deActivation, incoming.timestamp()));
    }
    ProcessDefinitionActivation activation =
        new ProcessDefinitionActivation(processDefinition, ProcessDefinitionStateEnum.ACTIVE);
    context.forward(new Record<>(key, activation, incoming.timestamp()));
  }
}
