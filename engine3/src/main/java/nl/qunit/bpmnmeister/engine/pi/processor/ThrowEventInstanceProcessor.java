package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ThrowEventInstance;

@NoArgsConstructor
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent2, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  protected ThrowEventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, I flowNodeInstance, String inputFlowId, Variables2 variables) {
    return processSpecificThrowEventInstance(flowElements, flowNodeInstance, variables);
  }

  protected abstract InstanceResult processSpecificThrowEventInstance(
      FlowElements2 flowElements, I flowNodeInstance, Variables2 variables);
}
