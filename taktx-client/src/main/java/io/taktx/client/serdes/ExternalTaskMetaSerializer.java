package io.taktx.client.serdes;

import io.taktx.dto.TopicMetaDTO;

public class ExternalTaskMetaSerializer extends JsonSerializer<TopicMetaDTO> {

  public ExternalTaskMetaSerializer() {
    super(TopicMetaDTO.class);
  }
}
