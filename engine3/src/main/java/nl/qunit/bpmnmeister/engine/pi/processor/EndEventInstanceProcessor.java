package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.EndEventInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent2, EndEventInstance> {

  @Inject
  public EndEventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      EndEvent2 flowNode, EndEventInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processSpecificThrowEventInstance(
      FlowElements2 flowElements, EndEventInstance flowNodeInstance, Variables2 variables) {
    flowNodeInstance.setState(FlowNodeStateEnum.FINISHED);
    return new InstanceResult();
  }
}
