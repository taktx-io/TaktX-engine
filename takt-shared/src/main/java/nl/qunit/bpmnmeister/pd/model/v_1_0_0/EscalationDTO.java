package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class EscalationDTO {
  private final String id;
  private final String name;
  private final String code;

  @JsonCreator
  public EscalationDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("name") String name,
      @Nonnull @JsonProperty("code") String code) {
    this.id = id;
    this.name = name;
    this.code = code;
  }
}
