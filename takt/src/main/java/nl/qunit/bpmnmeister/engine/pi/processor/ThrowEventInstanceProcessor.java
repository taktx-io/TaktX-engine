package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;
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
    InstanceResult result = InstanceResult.empty();
    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinitions()
        .forEach(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent> intermediateCatchEvent =
                  flowElements.getIntermediateCatchEventWithName(linkEventDefinition.getName());
              intermediateCatchEvent.ifPresent(
                  event -> {
                    FLowNodeInstance<?> catchEventInstance =
                        new IntermediateCatchEventInstance(
                            flowNodeInstance.getParentInstance(), event);
                    FLowNodeInstanceInfo flowNodeInstanceInfo =
                        new FLowNodeInstanceInfo(catchEventInstance, Constants.NONE);
                    result.addNewFlowNodeInstance(flowNodeInstanceInfo);
                  });
            });
    result.merge(processStartSpecificThrowEventInstance(flowElements, flowNodeInstance, variables));
    return result;
  }

  protected abstract InstanceResult processStartSpecificThrowEventInstance(
      FlowElements flowElements, I flowNodeInstance, Variables variables);
}
