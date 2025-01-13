package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import com.flomaestro.engine.pd.model.ThrowEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.IntermediateCatchEventInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ThrowEventInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import java.time.Clock;
import java.util.Optional;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  protected ThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      String inputFlowId,
      Variables variables,
      ProcessingStatistics processingStatistics) {
    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinition()
        .ifPresent(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent> intermediateCatchEvent =
                  flowElements.getIntermediateCatchEventWithName(linkEventDefinition.getName());
              intermediateCatchEvent.ifPresent(
                  event -> {
                    FlowNodeInstance<?> catchEventInstance =
                        new IntermediateCatchEventInstance(
                            flowNodeInstance.getParentInstance(), event);
                    FlowNodeInstanceInfo flowNodeInstanceInfo =
                        new FlowNodeInstanceInfo(catchEventInstance, Constants.NONE);
                    directInstanceResult.addNewFlowNodeInstance(
                        processInstance, flowNodeInstanceInfo);
                  });
            });
    processStartSpecificThrowEventInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        variables,
        processingStatistics);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      Variables variables,
      ProcessingStatistics processingStatistics);
}
