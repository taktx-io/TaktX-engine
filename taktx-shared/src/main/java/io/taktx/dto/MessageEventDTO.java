package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.MessageEventTypeIdResolver;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(MessageEventTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@ToString
public abstract class MessageEventDTO {
  @JsonProperty("m")
  private String messageName;

  protected MessageEventDTO(String messageName) {
    this.messageName = messageName;
  }

  @JsonIgnore
  public MessageEventKeyDTO toMessageEventKey() {
    return new MessageEventKeyDTO(messageName);
  }
}
