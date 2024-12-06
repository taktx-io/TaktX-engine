package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.StartCommand;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
    @JsonSubTypes.Type(value = XmlDefinitionsDTO.class),
    @JsonSubTypes.Type(value = ParsedDefinitionsDTO.class),
  @JsonSubTypes.Type(value = StartCommand.class)
})
@Getter
@EqualsAndHashCode
public abstract class DefinitionsTrigger {
  protected DefinitionsTrigger() {}
}
