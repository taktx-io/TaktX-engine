package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.Task;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.TaskInstance;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task, TaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public TaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      TaskInstance instance,
      ProcessInstance processInstance,
      FlowNodeInstanceVariables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // Nothing to do here
  }

  @Override
  protected void processStartSpecificActivityInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      TaskInstance flowNodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      FlowNodeInstanceVariables variables,
      ProcessingStatistics processingStatistics) {
    flowNodeInstance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      FlowNodeInstanceVariables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // Nothing to do here
  }
}
