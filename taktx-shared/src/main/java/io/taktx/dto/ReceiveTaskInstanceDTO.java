package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReceiveTaskInstanceDTO extends TaskInstanceDTO {

  @JsonProperty("c")
  private String correlationKey;

  @JsonProperty("m")
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;
}
