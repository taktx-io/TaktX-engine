/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.ingesters.fullupdatescassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.utility.DockerImageName;

public class CassandraTestResource implements QuarkusTestResourceLifecycleManager {
  private CassandraContainer cassandra;

  @Override
  public Map<String, String> start() {
    cassandra = new CassandraContainer(DockerImageName.parse("cassandra:4.1"));
    cassandra.start();

    // create keyspace and tables using a temporary session
    try (CqlSession session =
        CqlSession.builder()
            .addContactPoint(
                new InetSocketAddress(cassandra.getHost(), cassandra.getFirstMappedPort()))
            .withLocalDatacenter("datacenter1")
            .build()) {
      session.execute(
          "CREATE KEYSPACE IF NOT EXISTS taktx WITH replication = {'class':'SimpleStrategy','replication_factor':'1'}");
      // Keyspace used by the migration runner
      session.execute(
          "CREATE KEYSPACE IF NOT EXISTS taktx_instanceupdates WITH replication = {'class':'SimpleStrategy','replication_factor':'1'}");
      session.execute(
          "CREATE TABLE IF NOT EXISTS taktx.full_instance_events (process_instance_id uuid, kafka_partition int, kafka_offset bigint, event_timestamp bigint, update_json text, PRIMARY KEY ((process_instance_id), kafka_partition, kafka_offset)) WITH CLUSTERING ORDER BY (kafka_partition ASC, kafka_offset ASC)");
      session.execute(
          "CREATE TABLE IF NOT EXISTS taktx.variable_updates_by_instance_and_name (process_instance_id uuid, variable_name text, kafka_partition int, kafka_offset bigint, event_timestamp bigint, value_text text, PRIMARY KEY ((process_instance_id), variable_name, kafka_partition, kafka_offset)) WITH CLUSTERING ORDER BY (variable_name ASC, kafka_partition ASC, kafka_offset ASC)");
      session.execute(
          "CREATE TABLE IF NOT EXISTS taktx.variable_updates_by_name_value (variable_name text, value_text text, process_instance_id uuid, kafka_partition int, kafka_offset bigint, event_timestamp bigint, PRIMARY KEY ((variable_name, value_text), process_instance_id, kafka_partition, kafka_offset)) WITH CLUSTERING ORDER BY (process_instance_id ASC, kafka_partition ASC, kafka_offset ASC)");
    }

    String cp = String.format("%s:%d", cassandra.getHost(), cassandra.getFirstMappedPort());
    Map<String, String> props = new HashMap<>();
    props.put("quarkus.cassandra.contact-points", cp);
    props.put("quarkus.cassandra.local-datacenter", "datacenter1");
    props.put("quarkus.cassandra.keyspace", "taktx");
    // disable real TaktXClient during tests to avoid starting Kafka consumers
    props.put("taktx.client.enabled", "false");
    return props;
  }

  @Override
  public void stop() {
    if (cassandra != null) {
      cassandra.stop();
    }
  }
}
