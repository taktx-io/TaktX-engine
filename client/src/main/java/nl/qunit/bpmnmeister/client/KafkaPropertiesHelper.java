package nl.qunit.bpmnmeister.client;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class KafkaPropertiesHelper {

  @ConfigProperty(name = "deployer.kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "deployer.kafka.sasl.mechanism")
  Optional<String> kafkaSaslMechanism;

  @ConfigProperty(name = "deployer.kafka.sasl.jaas.config")
  Optional<String> kafkaSaslJaasConfig;

  @ConfigProperty(name = "deployer.kafka.security.protocol")
  Optional<String> kafkaSecurityProtocol;

  public Properties getKafkaConsumerProperties(
      String groupId, Class<?> keyDeserializer, Class<?> valueDeserializer) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());

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
}
