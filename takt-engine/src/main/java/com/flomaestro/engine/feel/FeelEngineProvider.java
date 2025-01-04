package com.flomaestro.engine.feel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

@ApplicationScoped
public class FeelEngineProvider {
  public static final FeelEngineApi FEEL_ENGINE_API = FeelEngineBuilder.forJava().build();

  @Produces
  public FeelEngineApi getFeelEngineApi() {
    return FEEL_ENGINE_API;
  }
}
