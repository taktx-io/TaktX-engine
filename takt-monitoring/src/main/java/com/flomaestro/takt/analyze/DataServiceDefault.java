package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.exceptions.InfluxException;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
@Startup
public class DataServiceDefault implements IDataService, AutoCloseable {
  private InfluxDBClient influxDBClient;

  @ConfigProperty(name = "influxdb.connectionUrl")
  String connectionUrl;

  @ConfigProperty(name = "influxdb.token")
  String token;

  @ConfigProperty(name = "influxdb.orgId")
  String orgId;

  @ConfigProperty(name = "influxdb.data.bucketId")
  String bucketId;

  public DataServiceDefault() {}

  @PostConstruct
  private void initializeInfluxDBClient() {
    log.info(
        "Connecting to: {}, token: {}, org: {}, bucketId: {}",
        connectionUrl,
        token,
        orgId,
        bucketId);
    this.influxDBClient =
        InfluxDBClientFactory.create(connectionUrl, token.toCharArray(), orgId, bucketId);
  }

  @Override
  public void close() throws Exception {
    this.influxDBClient.close();
  }

  @Override
  public void createData(long epochMilli, ProcessingStatisticsDTO dataInDTO)
      throws DataServiceException {
    try {
      WriteApi writeApi = influxDBClient.getWriteApi();
      Data data = new Data();
      data.setTime(Instant.ofEpochMilli(epochMilli));
      data.setFlowNodesContinued(dataInDTO.getFlowNodesContinued());
      data.setFlowNodesFinished(dataInDTO.getFlowNodesFinished());
      data.setFlowNodesStarted(dataInDTO.getFlowNodesStarted());
      data.setProcessInstancesFinished(dataInDTO.getProcessInstancesFinished());
      data.setProcessInstancesStarted(dataInDTO.getProcessInstancesStarted());
      data.setAverageRequestLatencyMs(dataInDTO.getAverageRequestLatencyMs());
      writeApi.writeMeasurement(WritePrecision.NS, data);
      writeApi.close();
    } catch (InfluxException ie) {
      throw new DataServiceException("Error while writing data to Influx: " + ie.getMessage());
    }
  }

  @Override
  public void createProcessInstanceData(
      long timestamp, String processDefinitionId, long averageProcessingTime, int size)
      throws DataServiceException {
    try {
      WriteApi writeApi = influxDBClient.getWriteApi();
      ProcessInstanceData data = new ProcessInstanceData();
      data.setTime(Instant.ofEpochMilli(timestamp));
      data.setDefinitionId(processDefinitionId);
      data.setAverageProcessingTime(averageProcessingTime);
      writeApi.writeMeasurement(WritePrecision.NS, data);
      writeApi.close();
    } catch (InfluxException ie) {
      throw new DataServiceException("Error while writing data to Influx: " + ie.getMessage());
    }
  }
}
