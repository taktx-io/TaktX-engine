package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class EventDefinitionDTO extends RootElementDTO {
  @JsonCreator
  protected EventDefinitionDTO(
      @Nonnull @JsonProperty("id") String id, @Nonnull @JsonProperty("parentId") String parentId) {
    super(id, parentId);
  }
}
