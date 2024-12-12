package com.flomaestro.client;

import com.flomaestro.takt.Topics;
import java.util.Optional;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

public class KafkaPropertiesHelper {
  private final String bootstrapServers;

  private final String tenant;

  private final String namespace;

  private final Optional<String> kafkaSaslMechanism;

  private final Optional<String> kafkaSaslJaasConfig;

  private final Optional<String> kafkaSecurityProtocol;

  public KafkaPropertiesHelper(
      String bootstrapServers,
      String tenant,
      String namespace,
      Optional<String> kafkaSaslMechanism,
      Optional<String> kafkaSaslJaasConfig,
      Optional<String> kafkaSecurityProtocol) {
    this.bootstrapServers = bootstrapServers;
    this.tenant = tenant;
    this.namespace = namespace;
    this.kafkaSaslMechanism = kafkaSaslMechanism;
    this.kafkaSaslJaasConfig = kafkaSaslJaasConfig;
    this.kafkaSecurityProtocol = kafkaSecurityProtocol;
  }

  public KafkaPropertiesHelper(String bootstrapServers, String tenant, String namespace) {
    this(bootstrapServers, tenant, namespace, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public Properties getKafkaConsumerProperties(
      String groupId, Class<?> keyDeserializer, Class<?> valueDeserializer) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // or "latest"
    kafkaSaslMechanism.ifPresent(s -> props.put("sasl.mechanism", s));
    kafkaSaslJaasConfig.ifPresent(s -> props.put("sasl.jaas.config", s));
    kafkaSecurityProtocol.ifPresent(s -> props.put("security.protocol", s));
    return props;
  }

  public Properties getKafkaProducerProperties(
      Class<? extends Serializer<?>> keySserializer,
      Class<? extends Serializer<?>> valueSerializer) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySserializer.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());

    kafkaSaslMechanism.ifPresent(s -> props.put("sasl.mechanism", s));
    kafkaSaslJaasConfig.ifPresent(s -> props.put("sasl.jaas.config", s));
    kafkaSecurityProtocol.ifPresent(s -> props.put("security.protocol", s));
    return props;
  }

  public String getPrefixedTopicName(Topics topics) {
    return tenant + "." + namespace + "." + topics.getTopicName();
  }
}
