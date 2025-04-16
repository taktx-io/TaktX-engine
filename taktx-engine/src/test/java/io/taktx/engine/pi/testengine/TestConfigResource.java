package io.taktx.engine.pi.testengine;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class TestConfigResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    // Set the property before the application starts
    return Map.of(
        "taktx.test", "true",
        "kafka.devservices.auto-create-topics", "false");
  }

  @Override
  public void stop() {
    // Cleanup logic if needed
  }
}
