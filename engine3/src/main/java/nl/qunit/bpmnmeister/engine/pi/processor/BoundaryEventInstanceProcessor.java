package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
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
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper, feelExpressionHandler);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return true;
  }

  @Override
  protected InstanceResult processContinueSpecificCatchEventInstance(
      BoundaryEventInstance boundaryEventInstance) {
    InstanceResult result = InstanceResult.empty();
    if (shouldCancel(boundaryEventInstance)) {
      result.addTerminateInstance(boundaryEventInstance.getAttachedInstanceId());
    }
    return result;
  }

  @Override
  protected boolean shouldCancel(BoundaryEventInstance flowNodeInstance) {
    return flowNodeInstance.getFlowNode().isCancelActivity();
  }
}
