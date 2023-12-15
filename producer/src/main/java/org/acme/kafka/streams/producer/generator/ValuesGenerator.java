package org.acme.kafka.streams.producer.generator;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.model.processdefinition.ProcessDefinition;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * A bean producing random temperature data every second. The values are written to a Kafka topic
 * (temperature-values). Another topic contains the name of weather stations (weather-stations). The
 * Kafka configuration is specified in the application configuration.
 */
@ApplicationScoped
public class ValuesGenerator {

  @Inject
  @Channel("process-definition-input")
  Emitter<ProcessDefinition> emitter;

  @Startup
  void init() {}
}
