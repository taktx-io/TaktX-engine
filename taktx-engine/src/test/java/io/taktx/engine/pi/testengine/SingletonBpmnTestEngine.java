package io.taktx.engine.pi.testengine;

import io.taktx.engine.generic.FixedClockProducer;

public class SingletonBpmnTestEngine {
  private static BpmnTestEngine instance;

  private SingletonBpmnTestEngine() {
    // Initialize your resource here
  }

  public static BpmnTestEngine getInstance() {
    if (instance == null) {
      instance = new BpmnTestEngine(new FixedClockProducer().produceFixedClock());
      instance.init();
    }
    return instance;
  }

  // Add methods to access the resource
}
