package io.taktx.dto.subscriptions;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.dto.SubscriptionDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@RegisterForReflection
public abstract class AbstractBoundaryEventSubscriptionDTO extends SubscriptionDTO {
  private int order;
  private int boundaryEventIndex;
}
