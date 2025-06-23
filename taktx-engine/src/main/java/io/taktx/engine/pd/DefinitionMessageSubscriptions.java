/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.MessageEventKeyDTO;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class DefinitionMessageSubscriptions {
  private Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions;

  public DefinitionMessageSubscriptions(
      Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions) {
    this.definitions = definitions;
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions update(
      DefinitionMessageSubscriptionDTO messageSubscription) {
    Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> newDefinitions =
        new HashMap<>(definitions);
    newDefinitions.put(messageSubscription.toMessageEventKey(), messageSubscription);
    return new DefinitionMessageSubscriptions(newDefinitions);
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions remove(
      CancelDefinitionMessageSubscriptionDTO messageSubscription) {
    Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> newDefinitions =
        new HashMap<>(definitions);
    newDefinitions.remove(messageSubscription.toMessageEventKey());
    return new DefinitionMessageSubscriptions(newDefinitions);
  }
}
