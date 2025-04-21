package io.taktx.dto;

import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class CallActivityInstanceDTO extends ActivityInstanceDTO {

  private UUID childProcessInstanceId;
}
