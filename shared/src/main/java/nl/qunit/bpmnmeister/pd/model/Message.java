package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Message {
  private final String id;
  private final String name;

  @JsonCreator
  public Message(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name
  ) {
    this.id = id;
    this.name = name;
  }
}
