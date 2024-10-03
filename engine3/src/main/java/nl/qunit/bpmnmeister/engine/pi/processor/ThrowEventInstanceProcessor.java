package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ThrowEventInstance;

@NoArgsConstructor
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  protected ThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements flowElements, I flowNodeInstance, String inputFlowId, Variables variables) {
    return processStartSpecificThrowEventInstance(flowElements, flowNodeInstance, variables);
  }

  protected abstract InstanceResult processStartSpecificThrowEventInstance(
      FlowElements flowElements, I flowNodeInstance, Variables variables);
}
