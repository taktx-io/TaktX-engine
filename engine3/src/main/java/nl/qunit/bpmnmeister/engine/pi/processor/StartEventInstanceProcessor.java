package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent2, StartEventInstance> {

  @Inject
  public StartEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(StartEventInstance instance) {
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processStartSpecificCatchEventInstance(
      FlowElements2 flowElements, StartEventInstance flowNodeInstance, Variables2 variables) {
    return new InstanceResult();
  }
}
