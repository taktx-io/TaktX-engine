package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class RootElementDTO extends BaseElementDTO {
  protected RootElementDTO(String id, String parentId) {
    super(id, parentId);
  }
}
