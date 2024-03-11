package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Process extends RootElement {
  private FlowElements flowElements;

  @JsonCreator
  public Process(
      @JsonProperty("id") BaseElementId id,
      @JsonProperty("parentId") BaseElementId parentId,
      @JsonProperty("flowElements") FlowElements flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }

  public FlowElements getFlowElements() {
    return flowElements;
  }
}
