package io.taktx.engine.generic;

import io.taktx.dto.ProcessDefinitionKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class SignalDefinitionSubscriptionKeyDTO {
  private byte[] signalNameHash;
  private ProcessDefinitionKey processDefinitionKey;
  private String elementId;
}
