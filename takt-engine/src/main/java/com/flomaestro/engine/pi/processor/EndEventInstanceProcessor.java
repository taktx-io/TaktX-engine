package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.EndEvent;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.EndEventInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent, EndEventInstance> {

  @Inject
  public EndEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      EndEventInstance instance,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }

  @Override
  protected void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      EndEventInstance flowNodeInstance,
      Variables variables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }
}
