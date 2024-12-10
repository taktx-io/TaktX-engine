package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class InstanceUpdateDTO {

  private UUID processInstanceKey;

  protected InstanceUpdateDTO(UUID processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }
}
