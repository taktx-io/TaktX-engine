package org.acme.kafka.streams.producer.generator;

import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.smallrye.mutiny.Multi;
import nl.qunit.bpmnmeister.model.ProcessDefinition;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

/**
 * A bean producing random temperature data every second.
 * The values are written to a Kafka topic (temperature-values).
 * Another topic contains the name of weather stations (weather-stations).
 * The Kafka configuration is specified in the application configuration.
 */
@ApplicationScoped
public class ValuesGenerator {

    private List<ProcessDefinition> processDefinitions = List.of(new ProcessDefinition());


    @Outgoing("process-definition")
    public Multi<ProcessDefinition> weatherStations() {
        return Multi.createFrom().items(processDefinitions.stream());
    }

}