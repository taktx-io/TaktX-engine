package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class EscalationSubscriptionDTO {
  private String name;
  private String code;

  @JsonCreator
  public EscalationSubscriptionDTO(
      @Nonnull @JsonProperty("name") String name, @Nonnull @JsonProperty("code") String code) {
    this.name = name;
    this.code = code;
  }
}
