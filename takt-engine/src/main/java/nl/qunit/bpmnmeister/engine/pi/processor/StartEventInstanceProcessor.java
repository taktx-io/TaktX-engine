package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pd.model.StartEvent;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.StartEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      VariablesMapper variablesMapper,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler) {
    super(ioMappingProcessor, variablesMapper, processInstanceMapper, feelExpressionHandler);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return false;
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      StartEventInstance flowNodeInstance) {}

  @Override
  protected boolean shouldCancel(StartEventInstance flowNodeInstance) {
    return true;
  }
}
