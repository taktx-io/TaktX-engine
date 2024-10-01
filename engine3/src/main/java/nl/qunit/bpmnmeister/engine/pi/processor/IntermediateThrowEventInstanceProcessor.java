package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent2;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateThrowEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateThrowEventInstanceProcessor
    extends ThrowEventInstanceProcessor<IntermediateThrowEvent2, IntermediateThrowEventInstance> {

  @Inject
  public IntermediateThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      IntermediateThrowEventInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processStartSpecificThrowEventInstance(
      FlowElements2 flowElements,
      IntermediateThrowEventInstance flowNodeInstance,
      Variables2 variables) {
    InstanceResult result = InstanceResult.empty();

    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinitions()
        .forEach(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent2> intermediateCatchEvent =
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
    return result;
  }
}
