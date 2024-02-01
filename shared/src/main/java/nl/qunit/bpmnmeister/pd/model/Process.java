package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Process extends RootElement {
  private Map<String, FlowElement> flowElements;

  @JsonCreator
  public Process(
      @JsonProperty("id") String id,
      @JsonProperty("flowElements") Map<String, FlowElement> flowElements) {
    super(id);
    this.flowElements = flowElements;
  }

  public Map<String, FlowElement> getFlowElements() {
    return flowElements;
  }
}
