package nl.qunit.bpmnmeister.engine.pi.feel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Builder;
import org.camunda.feel.impl.SpiServiceLoader;

@ApplicationScoped
public class FeelEngineProvider {

  public static final FeelEngine ENGINE =
      new Builder()
          .valueMapper(SpiServiceLoader.loadValueMapper())
          .functionProvider(SpiServiceLoader.loadFunctionProvider())
          .build();

  @Produces
  public FeelEngine getFeelEngine() {
    return ENGINE;
  }
}
