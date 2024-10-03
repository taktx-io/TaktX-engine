package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateCatchEventInstanceProcessor
    extends CatchEventInstanceProcessor<IntermediateCatchEvent, IntermediateCatchEventInstance> {

  @Inject
  IntermediateCatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper, feelExpressionHandler);
  }

  @Override
  protected InstanceResult processContinueSpecificCatchEventInstance(
      IntermediateCatchEventInstance flowNodeInstance) {
    return InstanceResult.empty();
  }

  @Override
  protected boolean shouldCancel(IntermediateCatchEventInstance flowNodeInstance) {
    return true;
  }
}
