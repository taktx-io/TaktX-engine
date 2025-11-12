/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

// language: java
package io.taktx.ingesters.fullupdatescassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Simple migration runner for Cassandra. Reads ordered migration files listed in
 * `db/migration/migration-list.txt` and applies any not yet recorded in `taktx.schema_migrations`.
 */
@ApplicationScoped
public class CassandraMigrationRunner {

  private static final Logger log = Logger.getLogger(CassandraMigrationRunner.class);
  private final CqlSession session;

  public CassandraMigrationRunner(CqlSession session) {
    this.session = Objects.requireNonNull(session);
  }

  void onStart(@Observes @Priority(5000) StartupEvent ev) {
    try {
      ensureMigrationKeyspace();
      ensureMigrationsTable();
      applyMigrations();
      log.info("Cassandra migrations completed");
    } catch (Exception e) {
      log.error("Cassandra migration failed", e);
      // decide whether to rethrow to fail startup
    }
  }

  private void ensureMigrationKeyspace() {
    // create the keyspace used to track migrations if it doesn't exist
    session.execute(
        "CREATE KEYSPACE IF NOT EXISTS taktx_instanceupdates WITH replication = {'class':'SimpleStrategy','replication_factor':'1'}");
    log.debug("Ensured keyspace taktx_instanceupdates exists");
  }

  private void ensureMigrationsTable() {
    session.execute(
        "CREATE TABLE IF NOT EXISTS taktx_instanceupdates.schema_migrations ("
            + "version text PRIMARY KEY, "
            + "description text, "
            + "installed_at timestamp)");
    log.debug("Ensured taktx.schema_migrations table");
  }

  private void applyMigrations() throws Exception {
    List<String> migrationFiles = readMigrationList();
    for (String file : migrationFiles) {
      if (isApplied(file)) {
        log.debugf("Migration already applied: %s", file);
        continue;
      }
      log.infov("Applying migration: {0}", file);
      String cql = readResource("/db/migration/" + file);
      executeCqlStatements(cql);
      recordApplied(file, extractDescription(file));
      log.infov("Applied migration: {0}", file);
    }
  }

  private List<String> readMigrationList() throws IOException {
    try (InputStream is = getClass().getResourceAsStream("/db/migration/migration-list.txt")) {
      if (is == null) {
        return List.of();
      }
      try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
        return r.lines()
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
            .collect(Collectors.toList());
      }
    }
  }

  private boolean isApplied(String version) {
    var rs =
        session.execute(
            "SELECT version FROM taktx_instanceupdates.schema_migrations WHERE version = '"
                + escape(version)
                + "' LIMIT 1");
    return rs.one() != null;
  }

  private void executeCqlStatements(String cql) {
    // simple split by ';' — keep statements idempotent where possible
    for (String stmt : cql.split(";")) {
      String trimmed = stmt.trim();
      if (trimmed.isEmpty()) continue;
      session.execute(trimmed);
    }
  }

  private void recordApplied(String version, String description) {
    // Use IF NOT EXISTS to avoid concurrent writers double-inserting
    String insert =
        "INSERT INTO taktx_instanceupdates.schema_migrations (version, description, installed_at) VALUES ('"
            + escape(version)
            + "', '"
            + escape(description)
            + "', "
            + "toTimestamp(now())"
            + ") IF NOT EXISTS";
    session.execute(insert);
  }

  private String readResource(String path) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) throw new IllegalStateException("Migration file not found: " + path);
      try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
        return r.lines().collect(Collectors.joining("\n"));
      }
    }
  }

  private String extractDescription(String filename) {
    // filename like V1__create_tables.cql -> "create_tables"
    int idx = filename.indexOf("__");
    if (idx > 0) {
      String rest = filename.substring(idx + 2);
      int dot = rest.lastIndexOf('.');
      return dot > 0 ? rest.substring(0, dot) : rest;
    }
    return filename;
  }

  private String escape(String s) {
    return s.replace("'", "''");
  }
}
