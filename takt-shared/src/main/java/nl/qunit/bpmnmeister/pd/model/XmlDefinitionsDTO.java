package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder
public class XmlDefinitionsDTO extends DefinitionsTrigger {

  private final String xml;

  @JsonCreator
  public XmlDefinitionsDTO(
      @JsonProperty("xml") @Nonnull String xml) {
    this.xml = xml;
  }
}
