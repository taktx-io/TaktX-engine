package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity, I extends ActivityInstance<E>, C extends ContinueFlowElementTrigger>
    extends FLowNodeInstanceProcessor<E, I, C> {

  protected ActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected final InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements flowElements, I flownodeInstance, String inputFlowId, Variables variables) {
    InstanceResult result = InstanceResult.empty();

    result.merge(
        processStartSpecificActivityInstance(
            flowElements, flownodeInstance, inputFlowId, variables));

    if (flownodeInstance.getState() == ActtivityStateEnum.WAITING) {
      E flowNode = flownodeInstance.getFlowNode();
      flowNode
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(flownodeInstance.getParentInstance(), boundaryEvent);
                boundaryEventInstance.setAttachedInstanceId(
                    flownodeInstance.getElementInstanceId());
                flownodeInstance.addBoundaryEventId(boundaryEventInstance.getElementInstanceId());
                result.addNewFlowNodeInstance(
                    new FLowNodeInstanceInfo(boundaryEventInstance, Constants.NONE));
              });
    }

    return result;
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      C trigger,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {

    InstanceResult result = InstanceResult.empty();
    result.merge(
        processContinueSpecificActivityInstance(
            subProcessLevel, flowElements, flowNodeInstance, trigger, processInstanceVariables));

    if (flowNodeInstance.getState() == ActtivityStateEnum.FINISHED) {
      flowNodeInstance.getBoundaryEventIds().forEach(result::addTerminateInstance);
    }

    return result;
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(I instance) {
    return processTerminateSpecificActivityInstance(instance);
  }

  protected abstract InstanceResult processStartSpecificActivityInstance(
      FlowElements flowElements, I flownodeInstance, String inputFlowId, Variables variables);

  protected abstract InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements flowElements,
      //      E externalTask,
      I externalTaskInstance,
      C trigger,
      Variables processInstanceVariables);

  protected abstract InstanceResult processTerminateSpecificActivityInstance(I instance);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      I flowNodeInstance,
      FlowElements flowElements,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}
