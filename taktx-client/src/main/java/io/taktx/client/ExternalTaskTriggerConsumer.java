package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.Set;

public interface ExternalTaskTriggerConsumer {

  Set<String> getJobIds();

  void accept(ExternalTaskTriggerDTO value);
}
