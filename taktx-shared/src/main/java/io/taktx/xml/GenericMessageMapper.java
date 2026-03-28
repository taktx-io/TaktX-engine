/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TMessage;
import io.taktx.dto.MessageDTO;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = null;
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
