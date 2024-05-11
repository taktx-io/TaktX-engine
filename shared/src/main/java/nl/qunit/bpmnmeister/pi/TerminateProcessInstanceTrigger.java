package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nl.qunit.bpmnmeister.pd.model.Constants;

public class TerminateProcessInstanceTrigger extends ProcessInstanceTrigger {
  @JsonCreator
  public  TerminateProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey) {
    super(processInstanceKey, Constants.NONE, Variables.EMPTY);
  }
}
