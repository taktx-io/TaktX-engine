package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.CustomTypeIdResolver;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonTypeInfo(use = Id.CUSTOM, property = "cls")
@JsonTypeIdResolver(CustomTypeIdResolver.class)
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public abstract class MessageEventDTO {
  @JsonProperty("mn")
  private String messageName;

  protected MessageEventDTO(String messageName) {
    this.messageName = messageName;
  }

  @JsonIgnore
  public MessageEventKeyDTO toMessageEventKey() {
    return new MessageEventKeyDTO(messageName);
  }
}
