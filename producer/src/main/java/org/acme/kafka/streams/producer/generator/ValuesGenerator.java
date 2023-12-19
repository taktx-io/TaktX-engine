package org.acme.kafka.streams.producer.generator;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * A bean producing random temperature data every second. The values are written to a Kafka topic
 * (temperature-values). Another topic contains the name of weather stations (weather-stations). The
 * Kafka configuration is specified in the application configuration.
 */
@ApplicationScoped
public class ValuesGenerator {
  Logger LOG = Logger.getLogger(ValuesGenerator.class);

  @Inject
  @Channel("trigger-outgoing")
  Emitter<Trigger> emitter;

  @Startup
  void init() {
    for (int i = 0; i < 2; i++) {
      UUID processInstanceId = UUID.randomUUID();
      LOG.info("Sending trigger " + processInstanceId);
      emitter.send(new Trigger(processInstanceId, "StartEvent_1", null));
    }
  }
}
