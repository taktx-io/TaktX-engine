package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {

  public abstract TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      BaseElement element,
      BpmnElementState oldState,
      Map<String, JsonNode> variables);

  public abstract S initialState();

  public abstract S terminate(S oldState);
}
