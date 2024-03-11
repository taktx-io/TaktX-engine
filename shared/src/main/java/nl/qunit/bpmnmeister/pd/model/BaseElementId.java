package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class BaseElementId {
  public static final BaseElementId NONE = new BaseElementId("_");

  private final String id;

  @JsonCreator
  public BaseElementId(
      @Nonnull @JsonProperty("id") String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "BaseElementId{" + "id='" + id + '\'' + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BaseElementId that = (BaseElementId) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
