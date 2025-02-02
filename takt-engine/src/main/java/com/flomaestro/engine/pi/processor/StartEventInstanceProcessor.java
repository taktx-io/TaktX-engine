package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.StartEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.StartEventInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.CatchEventStateEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return false;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      StartEventInstance startEventInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    startEventInstance.setState(CatchEventStateEnum.FINISHED);
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      StartEventInstance flowNodeInstance,
      ProcessingStatistics processingStatistics) {}

  @Override
  protected boolean shouldCancel(StartEventInstance flowNodeInstance) {
    return true;
  }
}
