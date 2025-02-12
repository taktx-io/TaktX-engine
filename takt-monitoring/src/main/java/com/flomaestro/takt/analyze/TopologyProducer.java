package com.flomaestro.takt.analyze;

import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import com.flomaestro.takt.util.TaktUUIDSerde;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;

@ApplicationScoped
@RequiredArgsConstructor
public class TopologyProducer {
  public static final ObjectMapperSerde<ProcessingStatisticsDTO> STATISTICS_SERDE =
      new ObjectMapperSerde<>(ProcessingStatisticsDTO.class);
  public static final Serde<UUID> PROCESS_INSTANCE_KEY_SERDE = new TaktUUIDSerde();
  public static final ObjectMapperSerde<InstanceUpdateDTO> INSTANCE_UPDATE_SERDE =
      new ObjectMapperSerde<>(InstanceUpdateDTO.class);

  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final IDataService dataService;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.INSTANCE_UPDATE_TOPIC.getTopicName()),
            Consumed.with(PROCESS_INSTANCE_KEY_SERDE, INSTANCE_UPDATE_SERDE))
        .process(() -> new InstanceUpdateProcessor(dataService));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.PROCESSING_STATISTICS_TOPIC.getTopicName()),
            Consumed.with(Serdes.String(), STATISTICS_SERDE))
        .process(() -> new ProcessingStatisticsProcessor(dataService));

    return builder.build();
  }
}
