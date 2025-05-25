package io.taktx.client;

import io.taktx.dto.TopicMetaDTO;

public interface ExternalTaskMetaConsumer {
  void accept(String jobId, TopicMetaDTO meta);
}
