package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent2, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      StartEvent2 flowNode, StartEventInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processSpecificCatchEventInstance(
      FlowElements2 flowElements, StartEventInstance flowNodeInstance, Variables2 variables) {
    flowNodeInstance.setState(FlowNodeStateEnum.FINISHED);
    return new InstanceResult();
  }
}
