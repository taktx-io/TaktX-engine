package io.taktx.dto.subscriptions;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.dto.SubscriptionDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class CatchAllEscalationSubscriptionDTO extends SubscriptionDTO {}
