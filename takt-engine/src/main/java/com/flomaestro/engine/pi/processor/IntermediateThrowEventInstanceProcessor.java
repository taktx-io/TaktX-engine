package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.IntermediateThrowEventInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateThrowEventInstanceProcessor
    extends ThrowEventInstanceProcessor<IntermediateThrowEvent, IntermediateThrowEventInstance> {

  @Inject
  public IntermediateThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      IntermediateThrowEventInstance instance,
      ProcessInstance processInstance,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }

  @Override
  protected void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      IntermediateThrowEventInstance flowNodeInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }
}
