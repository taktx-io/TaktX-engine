package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class Process extends RootElement {
  public static final Process NONE = new Process(Constants.NONE, Constants.NONE, FlowElements.EMPTY);
  private final FlowElements flowElements;

  @JsonCreator
  public Process(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
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
    if (!super.equals(o)) {
      return false;
    }
    Process process = (Process) o;
    return Objects.equals(flowElements, process.flowElements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), flowElements);
  }
}
