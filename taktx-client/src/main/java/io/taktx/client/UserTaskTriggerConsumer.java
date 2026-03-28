/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.UserTaskTriggerDTO;

/** A consumer interface for handling UserTaskTriggerDTO objects. */
public interface UserTaskTriggerConsumer {

  /**
   * Accepts a UserTaskTriggerDTO object for processing.
   *
   * @param value the UserTaskTriggerDTO object to be processed
   */
  void accept(UserTaskTriggerDTO value);
}
