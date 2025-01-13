package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.Task;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.TaskInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task, TaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public TaskInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      TaskInstance instance,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // Nothing to do here
  }

  @Override
  protected void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      TaskInstance flowNodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables,
      ProcessingStatistics processingStatistics) {
    flowNodeInstance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    // Nothing to do here
  }
}
