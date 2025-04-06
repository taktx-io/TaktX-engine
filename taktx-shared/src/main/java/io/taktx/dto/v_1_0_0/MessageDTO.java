package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class MessageDTO {
  @JsonProperty("id")
  private String id;

  @JsonProperty("nm")
  private String name;

  @JsonProperty("ck")
  private String correlationKey;

  public MessageDTO(String id, String name, String correlationKey) {
    this.id = id;
    this.name = name;
    this.correlationKey = correlationKey;
  }
}
