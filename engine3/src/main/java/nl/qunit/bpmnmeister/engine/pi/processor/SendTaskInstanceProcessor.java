package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.SendTask;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.SendTaskInstance;

@ApplicationScoped
@NoArgsConstructor
public class SendTaskInstanceProcessor
    extends ExternalTaskInstanceProcessor<SendTask, SendTaskInstance> {
  @Inject
  public SendTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper) {
    super(feelExpressionHandler, clock, ioMappingProcessor, variablesMapper);
  }
}
