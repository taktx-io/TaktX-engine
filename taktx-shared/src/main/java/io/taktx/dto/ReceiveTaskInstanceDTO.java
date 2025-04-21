package io.taktx.dto;

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

  private String correlationKey;

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;
}
