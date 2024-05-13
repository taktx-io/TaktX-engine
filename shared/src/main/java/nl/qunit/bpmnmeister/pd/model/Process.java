package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Process extends RootElement {
  public static final Process NONE =
      new Process(Constants.NONE, Constants.NONE, FlowElements.EMPTY);
  private final FlowElements flowElements;

  @JsonCreator
  public Process(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("flowElements") FlowElements flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }
}
