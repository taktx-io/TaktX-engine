package io.taktx.dto.subscriptions;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.dto.SubscriptionDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RegisterForReflection
public class EscalationSubscriptionDTO extends SubscriptionDTO {
  private String code;
}
