package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class ContainerTestResource implements QuarkusTestResourceLifecycleManager {

  /**
   * @return A map of system properties that should be set for the running tests
   */
  @Override
  public Map<String, String> start() {
    return Map.of();
  }

  @Override
  public void stop() {

  }
}