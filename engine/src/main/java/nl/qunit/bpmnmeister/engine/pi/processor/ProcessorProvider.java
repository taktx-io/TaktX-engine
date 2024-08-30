package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pi.processor.flowelement.BoundaryEventProcessor;
import nl.qunit.bpmnmeister.pd.model.ActivityDTO;
import nl.qunit.bpmnmeister.pd.model.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.BoundaryEventDTO;
import nl.qunit.bpmnmeister.pd.model.CallActivityDTO;
import nl.qunit.bpmnmeister.pd.model.CatchEventDTO;
import nl.qunit.bpmnmeister.pd.model.EndEventDTO;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.InclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ParallelGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.ReceiveTaskDTO;
import nl.qunit.bpmnmeister.pd.model.SendTaskDTO;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;
import nl.qunit.bpmnmeister.pd.model.StartEventDTO;
import nl.qunit.bpmnmeister.pd.model.SubProcessDTO;
import nl.qunit.bpmnmeister.pd.model.TaskDTO;
import nl.qunit.bpmnmeister.pd.model.ThrowEventDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;

@ApplicationScoped
public class ProcessorProvider {

  @Inject StartEventProcessor startEventProcessor;
  @Inject IntermediateCatchEventProcessor intermediateCatchEventProcessor;
  @Inject IntermediateThrowEventProcessor intermediateThrowEventProcessor;
  @Inject EndEventProcessor endEventProcessor;
  @Inject ExclusiveGatewayProcessor exclusiveGatewayProcessor;
  @Inject ParallelGatewayProcessor parallelGatewayProcessor;
  @Inject InclusiveGatewayProcessor inclusiveGatewayProcessor;
  @Inject ServiceTaskProcessor serviceTaskProcessor;
  @Inject BoundaryEventProcessor boundaryEventProcessor;
  @Inject TaskProcessor taskProcessor;
  @Inject SubProcessProcessor subProcessProcessor;
  @Inject CallActivityProcessor callActivityProcessor;
  @Inject SendTaskProcessor sendTaskProcessor;
  @Inject ReceiveTaskProcessor receiveTaskProcessor;
  @Inject FeelExpressionHandler feelExpressionHandler;

  public StateProcessor<?, ?> getProcessor(BaseElementDTO element) {
    if (element instanceof ThrowEventDTO<?> throwEvent) {
      return getProcessorForThrowEvent(throwEvent);
    } else if (element instanceof CatchEventDTO<?> catchEvent) {
      return getProcessorForCatchEvent(catchEvent);
    } else if (element instanceof ExclusiveGatewayDTO) {
      return exclusiveGatewayProcessor;
    } else if (element instanceof InclusiveGatewayDTO) {
      return inclusiveGatewayProcessor;
    } else if (element instanceof ParallelGatewayDTO) {
      return parallelGatewayProcessor;
    } else if (element instanceof ActivityDTO activity) {
      return getStateProcessorForActivity(activity);
    }

    throw new IllegalStateException("Unknown element type: " + element.getClass());
  }

  private StateProcessor<?, ?> getProcessorForThrowEvent(ThrowEventDTO<?> throwEvent) {
    if (throwEvent instanceof EndEventDTO) {
      return endEventProcessor;
    } else if (throwEvent instanceof IntermediateThrowEvent) {
      return intermediateThrowEventProcessor;
    }
    throw new IllegalStateException("Unknown throw element type: " + throwEvent.getClass());
  }

  private StateProcessor<?, ?> getProcessorForCatchEvent(CatchEventDTO<?> element) {
    if (element instanceof StartEventDTO) {
      return startEventProcessor;
    } else if (element instanceof IntermediateCatchEvent) {
      return intermediateCatchEventProcessor;
    } else if (element instanceof BoundaryEventDTO) {
      return boundaryEventProcessor;
    }
    throw new IllegalStateException("Unknown catch event element type: " + element.getClass());
  }

  private StateProcessor<? extends BaseElementDTO, ? extends FlowNodeStateDTO>
      getStateProcessorForActivity(ActivityDTO element) {
    ActivityProcessor<? extends ActivityDTO, ? extends FlowNodeStateDTO> processor = null;
    if (element instanceof ServiceTaskDTO) {
      processor = serviceTaskProcessor;
    } else if (element instanceof SendTaskDTO) {
      processor = sendTaskProcessor;
    } else if (element instanceof SubProcessDTO) {
      processor = subProcessProcessor;
    } else if (element instanceof CallActivityDTO) {
      processor = callActivityProcessor;
    } else if (element instanceof ReceiveTaskDTO) {
      processor = receiveTaskProcessor;
    } else if (element instanceof TaskDTO) {
      // This must be the last check, as Task is the superclass of all other tasks
      processor = taskProcessor;
    }
    if (!element.getLoopCharacteristics().equals(LoopCharacteristicsDTO.NONE)) {
      // Wrap in MultiInstance processor when the element has loop characteristics
      if (element.getLoopCharacteristics().isSequential()) {
        return new SequentialMultiInstanceProcessor(feelExpressionHandler, processor);
      } else {
        return new ParallelMultiInstanceProcessor(feelExpressionHandler, processor);
      }
    }
    return processor;
  }
}
