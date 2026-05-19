package io.taktx.engine.generic;

import java.util.List;
import java.util.UUID;
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
public class SignalInstanceSubscriptionKeyDTO {
  private byte[] signalNameHash;

  private UUID processInstanceId;

  private List<Long> elementInstanceIdPath;
}
