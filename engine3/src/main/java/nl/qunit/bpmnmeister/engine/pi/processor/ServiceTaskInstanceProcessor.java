package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.ServiceTask2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.ServiceTaskInstance;

@ApplicationScoped
@NoArgsConstructor
public class ServiceTaskInstanceProcessor
    extends ExternalTaskInstanceProcessor<ServiceTask2, ServiceTaskInstance> {
  @Inject
  public ServiceTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper) {
    super(feelExpressionHandler, clock, ioMappingProcessor, variablesMapper);
  }
}
