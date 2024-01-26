package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.*;

@ApplicationScoped
public class ProcessorProvider {
  @Inject StartEventProcessor startEventProcessor;
  @Inject EndEventProcessor endEventProcessor;
  @Inject ExclusiveGatewayProcessor exclusiveGatewayProcessor;
  @Inject ParallelGatewayProcessor parallelGatewayProcessor;
  @Inject ServiceTaskProcessor serviceTaskProcessor;
  @Inject TaskProcessor taskProcessor;

  public StateProcessor<?, ?> getProcessor(BaseElement element) {
    if (element instanceof StartEvent) {
      return startEventProcessor;
    } else if (element instanceof EndEvent) {
      return endEventProcessor;
    } else if (element instanceof ExclusiveGateway) {
      return exclusiveGatewayProcessor;
    } else if (element instanceof ParallelGateway) {
      return parallelGatewayProcessor;
    } else if (element instanceof ServiceTask) {
      return serviceTaskProcessor;
    } else if (element instanceof Task) {
      return taskProcessor;
    }
    return null;
  }
}
