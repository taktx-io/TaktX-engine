package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
public class DefinitionsKey {

  public static final DefinitionsKey NONE = new DefinitionsKey("", "");

  private String processDefinitionId;

  private String hash;

  public DefinitionsKey(String processDefinitionId, String hash) {
    this.processDefinitionId = processDefinitionId;
    this.hash = hash;
  }
}
