package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class EscalationSubscriptionDTO {
  @JsonProperty("n")
  private String name;

  @JsonProperty("c")
  private String code;

  public EscalationSubscriptionDTO(String name, String code) {
    this.name = name;
    this.code = code;
  }
}
