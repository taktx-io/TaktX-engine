package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.DirectInstanceResult;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class BoundaryEventInstanceProcessor
    extends CatchEventInstanceProcessor<BoundaryEvent, BoundaryEventInstance> {

  @Inject
  BoundaryEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper, processInstanceMapper, feelExpressionHandler);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return true;
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      BoundaryEventInstance boundaryEventInstance) {
    if (shouldCancel(boundaryEventInstance)) {
      directInstanceResult.addTerminateInstance(boundaryEventInstance.getAttachedInstanceId());
    }
  }

  @Override
  protected boolean shouldCancel(BoundaryEventInstance flowNodeInstance) {
    return flowNodeInstance.getFlowNode().isCancelActivity();
  }
}
