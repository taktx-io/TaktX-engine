package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcess, SubProcessState> {

  private static final Logger LOG = Logger.getLogger(SubProcessProcessor.class);

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Map<String, JsonNode> variables) {
    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();
    ProcessDefinition subProcessDefinition =
        getSubProcessDefinition(processInstance.getProcessDefinition(), element);
    BaseElementId startElement = getStartEvent(element);

    ProcessInstanceTrigger subProcessTrigger =
        new ProcessInstanceTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            subProcessDefinition,
            startElement,
            false,
            null,
            trigger.getVariables());
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(StateEnum.WAITING, oldState.getElementInstanceId());
    return TriggerResult.builder()
        .newElementState(newSubProcessState)
        .newProcessInstanceTriggers(subProcessTriggers)
        .build();
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Map<String, JsonNode> variables) {
    SubProcessState newSubProcessState =
        new SubProcessState(StateEnum.FINISHED, oldState.getElementInstanceId());
    return TriggerResult.builder()
        .newElementState(newSubProcessState)
        .newActiveFlows(element.getOutgoing())
        .variables(variables)
        .build();
  }

  private static ProcessDefinition getSubProcessDefinition(
      ProcessDefinition processDefinition, SubProcess element) {
    BaseElementId subProcessDefinitionId =
        new BaseElementId(
            processDefinition.getDefinitions().getProcessDefinitionId().getId()
                + "-"
                + element.getId().getId());
    Definitions definitions =
        new Definitions(
            subProcessDefinitionId,
            processDefinition.getDefinitions().getGeneration(),
            processDefinition.getDefinitions().getHash(),
            element.getElements());
    return new ProcessDefinition(definitions, processDefinition.getVersion());
  }

  private BaseElementId getStartEvent(SubProcess element) {
    if (!element.getStartEvents().isEmpty()) {
      return element.getStartEvents().get(0).getId();
    } else {
      return element.getElements().get(0).getId();
    }
  }

  @Override
  public SubProcessState initialState() {
    return new SubProcessState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public SubProcessState terminate(SubProcessState oldState) {
    return new SubProcessState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
