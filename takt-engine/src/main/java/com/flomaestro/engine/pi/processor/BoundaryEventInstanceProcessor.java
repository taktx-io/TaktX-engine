package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.BoundaryEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;

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
