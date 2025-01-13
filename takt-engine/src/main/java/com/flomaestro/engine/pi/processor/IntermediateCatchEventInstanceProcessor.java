package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.IntermediateCatchEventInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateCatchEventInstanceProcessor
    extends CatchEventInstanceProcessor<IntermediateCatchEvent, IntermediateCatchEventInstance> {

  @Inject
  IntermediateCatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, variablesMapper, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return true;
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      IntermediateCatchEventInstance flowNodeInstance,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }

  @Override
  protected boolean shouldCancel(IntermediateCatchEventInstance flowNodeInstance) {
    return true;
  }
}
