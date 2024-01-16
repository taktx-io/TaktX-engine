package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.qunit.bpmnmeister.pd.model.Definitions;

@RegisterForReflection
public class Aggregation {
  public int version;

  public Aggregation updateFrom(Definitions definitions) {
    version++;
    return this;
  }
}
