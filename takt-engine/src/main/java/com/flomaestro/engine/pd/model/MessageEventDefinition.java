package com.flomaestro.engine.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class MessageEventDefinition extends EventDefinition implements WithMessageReference {

  private String messageRef;

  @Setter private Message referencedMessage;
}
