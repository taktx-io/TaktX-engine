package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.CatchEvent2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent2, I extends CatchEventInstance>
    extends EventInstanceProcessor<E, I> {

  protected CatchEventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, I flowNodeInstance, Variables2 variables) {
    return processSpecificCatchEventInstance(flowElements, flowNodeInstance, variables);
  }

  protected abstract InstanceResult processSpecificCatchEventInstance(
      FlowElements2 flowElements, I flowNodeInstance, Variables2 variables);
}
