package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SequenceFlow {
  String id;
  String target;
  String condition;
}
