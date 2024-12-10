package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
public abstract class BaseElementDTO {
  private final String id;
  private final String parentId;

  protected BaseElementDTO(@Nonnull String id, String parentId) {
    this.id = id;
    this.parentId = parentId;
  }
}
