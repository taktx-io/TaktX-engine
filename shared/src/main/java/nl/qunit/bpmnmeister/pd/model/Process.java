package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Process extends RootElement {
  private Map<BaseElementId, FlowElement> flowElements;

  @JsonCreator
  public Process(
      @JsonProperty("id") BaseElementId id,
      @JsonProperty("parentId") BaseElementId parentId,
      @JsonProperty("flowElements") Map<BaseElementId, FlowElement> flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }

  public Map<BaseElementId, FlowElement> getFlowElements() {
    return flowElements;
  }
}
