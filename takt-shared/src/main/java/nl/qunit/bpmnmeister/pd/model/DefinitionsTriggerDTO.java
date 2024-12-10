package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@Getter
@EqualsAndHashCode
public abstract class DefinitionsTriggerDTO {

  protected DefinitionsTriggerDTO() {
  }
}
