package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.SendTask;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.SendTaskInstance;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;

@ApplicationScoped
@NoArgsConstructor
public class SendTaskInstanceProcessor
    extends ExternalTaskInstanceProcessor<SendTask, SendTaskInstance> {
  @Inject
  public SendTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(feelExpressionHandler, clock, ioMappingProcessor, processInstanceMapper, variablesMapper);
  }
}
