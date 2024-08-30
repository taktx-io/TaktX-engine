package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ServiceTask2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.ServiceTaskInstance;

@ApplicationScoped
@NoArgsConstructor
public class ServiceTaskInstanceProcessor
    extends ExternalTaskInstanceProcessor<ServiceTask2, ServiceTaskInstance> {
  public ServiceTaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      Clock clock,
      IoMappingProcessor ioMappingProcessor) {
    super(feelExpressionHandler, clock, ioMappingProcessor);
  }
}
