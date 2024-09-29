package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class MessageEventDefinition2 extends EventDefinition2 implements WithMessageReference {

  private String messageRef;

  @Setter private Message2 referencedMessage;
}
