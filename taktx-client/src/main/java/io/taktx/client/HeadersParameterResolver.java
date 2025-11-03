package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.Map;

public class HeadersParameterResolver implements ParameterResolver {

  private final ObjectMapper objectMapper;
  private final Class<?> type;

  public HeadersParameterResolver(ObjectMapper objectMapper, Class<?> type) {
    this.objectMapper = objectMapper;
    this.type = type;
  }

  @Override
  public Object resolve(ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    Map<String, String> headers = externalTaskTriggerDTO.getHeaders();
    if (type == Map.class) {
      return headers;
    }
    return objectMapper.convertValue(headers, type);
  }
}
