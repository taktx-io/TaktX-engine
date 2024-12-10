package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.ServiceTaskInstance;

@ApplicationScoped
@NoArgsConstructor
public class ServiceTaskInstanceProcessor
    extends ExternalTaskInstanceProcessor<ServiceTask, ServiceTaskInstance> {
  @Inject
  public ServiceTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(feelExpressionHandler, clock, ioMappingProcessor, processInstanceMapper, variablesMapper);
  }
}
