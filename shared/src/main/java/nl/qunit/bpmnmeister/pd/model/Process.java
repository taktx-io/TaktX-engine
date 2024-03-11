package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class Process extends RootElement {
  public static final Process NONE = new Process(BaseElementId.NONE, BaseElementId.NONE, FlowElements.EMPTY);
  private final FlowElements flowElements;

  @JsonCreator
  public Process(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("flowElements") FlowElements flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Process process = (Process) o;
    return Objects.equals(flowElements, process.flowElements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowElements);
  }
}
