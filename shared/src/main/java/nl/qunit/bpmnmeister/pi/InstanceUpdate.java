package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProcessInstanceUpdate.class),
  @JsonSubTypes.Type(value = FlowNodeInstanceUpdate.class)
})
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class InstanceUpdate {

  private UUID processInstanceKey;

  protected InstanceUpdate(UUID processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }
}
