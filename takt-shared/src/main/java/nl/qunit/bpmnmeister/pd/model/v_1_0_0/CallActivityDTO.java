package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CallActivityDTO extends ActivityDTO {

  private final String calledElement;
  private final boolean propagateAllParentVariables;
  private final boolean propagateAllChildVariables;

  @JsonCreator
  public CallActivityDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristicsDTO loopCharacteristics,
      @Nonnull @JsonProperty("calledElement") String calledElement,
      @JsonProperty("propagateAllParentVariables") boolean propagateAllParentVariables,
      @JsonProperty("propagateAllChildVariables") boolean propagateAllChildVariables,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.calledElement = calledElement;
    this.propagateAllParentVariables = propagateAllParentVariables;
    this.propagateAllChildVariables = propagateAllChildVariables;
  }
}
