package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LinkEventDefinitionDTO extends EventDefinitionDTO {

  private final String name;

  @JsonCreator
  public LinkEventDefinitionDTO(
      @Nonnull @JsonProperty("id") String id, @Nonnull @JsonProperty("messageRef") String name) {
    super(id, Constants.NONE);
    this.name = name;
  }
}
