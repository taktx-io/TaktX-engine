package io.taktx.client;

import io.taktx.dto.ExternalTaskMetaDTO;

public interface ExternalTaskMetaConsumer {
  void accept(String jobId, ExternalTaskMetaDTO meta);
}
