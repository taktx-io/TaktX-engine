package io.taktx.client;

import io.taktx.dto.UserTaskTriggerDTO;

public interface UserTaskTriggerConsumer {

  void accept(UserTaskTriggerDTO value);
}
