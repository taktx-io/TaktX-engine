package nl.qunit.bpmnmeister.engine.pi.feel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.SpiServiceLoader;

@ApplicationScoped
public class FeelEngineProvider {

  @Produces
  public FeelEngine getFeelEngine() {
    return new FeelEngine.Builder()
        .valueMapper(SpiServiceLoader.loadValueMapper())
        .functionProvider(SpiServiceLoader.loadFunctionProvider())
        .build();
  }
}
