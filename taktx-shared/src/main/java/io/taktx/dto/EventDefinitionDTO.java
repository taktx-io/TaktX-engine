package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventDefinitionDTO extends RootElementDTO {
  protected EventDefinitionDTO(String id, String parentId) {
    super(id, parentId);
  }
}
