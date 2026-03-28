/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
