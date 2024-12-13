package com.flomaestro.takt.dto.v_1_0_0;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class VariableKeyDTO {
  private UUID processInstanceKey;
  private String variableName;
}
