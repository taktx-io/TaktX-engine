package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.engine.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstanceInfo;
import nl.qunit.bpmnmeister.engine.pi.model.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ThrowEventInstance;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pi.Variables;

@NoArgsConstructor
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  protected ThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      String inputFlowId,
      Variables variables) {
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
                    FlowNodeInstanceInfo flowNodeInstanceInfo =
                        new FlowNodeInstanceInfo(catchEventInstance, Constants.NONE);
                    directInstanceResult.addNewFlowNodeInstance(
                        processInstance, flowNodeInstanceInfo);
                  });
            });
    processStartSpecificThrowEventInstance(
        instanceResult, directInstanceResult, flowElements, flowNodeInstance, variables);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      Variables variables);
}
