package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.EndEvent;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.EndEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent, EndEventInstance> {

  @Inject
  public EndEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      EndEventInstance instance,
      ProcessInstance processInstance,
      FlowNodeInstanceVariables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }

  @Override
  protected void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      EndEventInstance flowNodeInstance,
      FlowNodeInstanceVariables variables,
      ProcessingStatistics processingStatistics) {
    // nothing to do
  }
}
