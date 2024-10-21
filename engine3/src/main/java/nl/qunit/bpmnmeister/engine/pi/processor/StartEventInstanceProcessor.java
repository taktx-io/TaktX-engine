package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper,
      FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, variablesMapper, feelExpressionHandler);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return false;
  }

  @Override
  protected InstanceResult processContinueSpecificCatchEventInstance(
      StartEventInstance flowNodeInstance) {
    return InstanceResult.empty();
  }

  @Override
  protected boolean shouldCancel(StartEventInstance flowNodeInstance) {
    return true;
  }
}
