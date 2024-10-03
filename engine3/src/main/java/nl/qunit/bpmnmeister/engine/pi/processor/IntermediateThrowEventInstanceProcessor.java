package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateThrowEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateThrowEventInstanceProcessor
    extends ThrowEventInstanceProcessor<IntermediateThrowEvent, IntermediateThrowEventInstance> {

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
      FlowElements flowElements,
      IntermediateThrowEventInstance flowNodeInstance,
      Variables variables) {
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
    return result;
  }
}
