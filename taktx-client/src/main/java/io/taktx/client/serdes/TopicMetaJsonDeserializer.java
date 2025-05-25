package io.taktx.client.serdes;

import io.taktx.dto.TopicMetaDTO;

public class TopicMetaJsonDeserializer extends JsonDeserializer<TopicMetaDTO> {
  public TopicMetaJsonDeserializer() {
    super(TopicMetaDTO.class);
  }
}
