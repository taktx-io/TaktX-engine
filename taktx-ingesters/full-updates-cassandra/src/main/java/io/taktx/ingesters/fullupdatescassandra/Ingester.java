/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.ingesters.fullupdatescassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.TaktXClient;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.VariablesDTO;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
@Startup
public class Ingester {
  private static final Logger log = Logger.getLogger(Ingester.class);

  @Inject Instance<TaktXClient> taktXClientInstance; // optional in tests

  @Inject CqlSession session;

  private PreparedStatement psFull;
  private PreparedStatement psVarByInstance;
  private PreparedStatement psVarByNameValue;

  private final ObjectMapper mapper = new ObjectMapper();

  void onStart(@Observes @Priority(2000) StartupEvent ev) {
    prepareStatements();

    if (taktXClientInstance != null && taktXClientInstance.isResolvable()) {
      TaktXClient taktXClient = taktXClientInstance.get();
      try {
        taktXClient.registerInstanceUpdateConsumer(
            "ingester-full-cassandra",
            instanceUpdateRecords -> {
              try {
                for (InstanceUpdateRecord rec : instanceUpdateRecords) {
                  persistRecord(rec);
                }
              } catch (Exception e) {
                log.error("Failed to persist instance update records", e);
              }
            });
      } catch (Exception e) {
        log.error("Failed to register instance update consumer", e);
      }
    } else {
      log.debug(
          "TaktXClient not available at startup; ingester consumer not registered (test mode)");
    }
  }

  // package-private for test access
  void prepareStatements() {
    // prepare insert for full events
    psFull =
        session.prepare(
            "INSERT INTO taktx.full_instance_events (process_instance_id, kafka_partition, kafka_offset, event_timestamp, update_json) VALUES (?,?,?,?,?)");

    psVarByInstance =
        session.prepare(
            "INSERT INTO taktx.variable_updates_by_instance_and_name (process_instance_id, variable_name, kafka_partition, kafka_offset, event_timestamp, value_text) VALUES (?,?,?,?,?,?)");

    psVarByNameValue =
        session.prepare(
            "INSERT INTO taktx.variable_updates_by_name_value (variable_name, value_text, process_instance_id, kafka_partition, kafka_offset, event_timestamp) VALUES (?,?,?,?,?,?)");
  }

  /** Public API: ingest a single InstanceUpdateRecord via CDI */
  public void ingest(InstanceUpdateRecord rec) {
    persistRecord(rec);
  }

  // package-private for test access
  void persistRecord(InstanceUpdateRecord rec) {
    try {
      String updateJson = mapper.writeValueAsString(rec.getUpdate());

      BatchStatementBuilder batch = new BatchStatementBuilder(DefaultBatchType.UNLOGGED);

      BoundStatement bFull =
          psFull.bind(
              rec.getProcessInstanceId(),
              rec.getKafkaPartition(),
              rec.getKafkaOffset(),
              rec.getTimestamp(),
              updateJson);
      batch.addStatement(bFull);

      // handle variables for FlowNode and ProcessInstance updates
      if (rec.getUpdate() instanceof FlowNodeInstanceUpdateDTO dto) {
        VariablesDTO vars = dto.getVariables();
        addVariableInserts(rec, vars, batch);
      } else if (rec.getUpdate() instanceof ProcessInstanceUpdateDTO dto) {
        VariablesDTO vars = dto.getVariables();
        addVariableInserts(rec, vars, batch);
      }

      session.execute(batch.build());
    } catch (Exception e) {
      log.errorf(
          e,
          "Error persisting record for instance %s (partition=%d, offset=%d)",
          rec.getProcessInstanceId(),
          rec.getKafkaPartition(),
          rec.getKafkaOffset());
    }
  }

  // package-private for test access
  void addVariableInserts(
      InstanceUpdateRecord rec, VariablesDTO vars, BatchStatementBuilder batch) {
    if (vars == null) return;
    Map<String, com.fasterxml.jackson.databind.JsonNode> map = vars.getVariables();
    if (map == null || map.isEmpty()) return;

    long ts = rec.getTimestamp();
    for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e : map.entrySet()) {
      String name = e.getKey();
      String valueText = e.getValue() == null ? null : e.getValue().toString();

      BoundStatement bVarInstance =
          psVarByInstance.bind(
              rec.getProcessInstanceId(),
              name,
              rec.getKafkaPartition(),
              rec.getKafkaOffset(),
              ts,
              valueText);
      batch.addStatement(bVarInstance);

      BoundStatement bNameValue =
          psVarByNameValue.bind(
              name,
              valueText,
              rec.getProcessInstanceId(),
              rec.getKafkaPartition(),
              rec.getKafkaOffset(),
              ts);
      batch.addStatement(bNameValue);
    }
  }
}
