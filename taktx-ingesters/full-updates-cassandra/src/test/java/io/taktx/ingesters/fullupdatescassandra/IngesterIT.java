package io.taktx.ingesters.fullupdatescassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.VariablesDTO;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(CassandraTestResource.class)
class IngesterIT {

  @Inject CqlSession session;

  @Inject Ingester ingester;

  @Test
  void testPersistFlowNodeWithVariables() {
    UUID instanceId = UUID.randomUUID();

    VariablesDTO vars = VariablesDTO.of(Map.of("foo", JsonNodeFactory.instance.numberNode(123)));
    FlowNodeInstanceUpdateDTO dto =
        new FlowNodeInstanceUpdateDTO(List.of(1L), null, vars, System.currentTimeMillis());

    InstanceUpdateRecord rec =
        new InstanceUpdateRecord(System.currentTimeMillis(), instanceId, dto, 0, 1L);

    // use public API (CDI-wired); Ingester will have prepared statements at startup
    ingester.ingest(rec);

    ResultSet rs =
        session.execute(
            session
                .prepare(
                    "SELECT update_json FROM taktx.full_instance_events WHERE process_instance_id = ?")
                .bind(instanceId));
    Row row = rs.one();
    assertNotNull(row, "full event row should exist");

    ResultSet rs2 =
        session.execute(
            session
                .prepare(
                    "SELECT value_text FROM taktx.variable_updates_by_instance_and_name WHERE process_instance_id = ? AND variable_name = ? LIMIT 1")
                .bind(instanceId, "foo"));
    Row row2 = rs2.one();
    assertNotNull(row2, "variable index row should exist");
    assertEquals("123", row2.getString("value_text"));
  }
}
