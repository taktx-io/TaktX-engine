package io.taktx.client.serdes;

import io.taktx.dto.UserTaskTriggerDTO;

public class UserTaskTriggerJsonDeserializer extends JsonDeserializer<UserTaskTriggerDTO> {
  public UserTaskTriggerJsonDeserializer() {
    super(UserTaskTriggerDTO.class);
  }
}
