package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;

public class EventDefinition extends RootElement {
  @JsonCreator
  protected EventDefinition(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId) {
    super(id, parentId);
  }
}
