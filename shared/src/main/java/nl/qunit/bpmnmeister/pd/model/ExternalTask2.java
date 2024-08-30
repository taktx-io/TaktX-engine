package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class ExternalTask2 extends Activity2 {
  private String workerDefinition;
  private String retries;
  private String implementation;
}
