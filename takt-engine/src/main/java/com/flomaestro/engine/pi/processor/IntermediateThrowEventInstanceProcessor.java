package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.IntermediateThrowEventInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateThrowEventInstanceProcessor
    extends ThrowEventInstanceProcessor<IntermediateThrowEvent, IntermediateThrowEventInstance> {

  @Inject
  public IntermediateThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      IntermediateThrowEventInstance instance,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {}

  @Override
  protected void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      IntermediateThrowEventInstance flowNodeInstance,
      Variables variables) {}
}
