package io.taktx.engine.generic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.dto.ProcessDefinitionKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class SignalDefinitionSubscriptionKeyDTO {
  private byte[] signalNameHash;
  private ProcessDefinitionKey processDefinitionKey;
  private String elementId;
}
