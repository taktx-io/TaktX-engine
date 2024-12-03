package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class MessageDTO {
  private final String id;
  private final String name;
  private final String correlationKey;

  @JsonCreator
  public MessageDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("name") String name,
      @Nonnull @JsonProperty("correlationKey") String correlationKey) {
    this.id = id;
    this.name = name;
    this.correlationKey = correlationKey;
  }
}
