package com.flomaestro.engine.pi.testengine;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

public class KafkaConsumerUtil<K, V> {
  private static final Logger LOG = Logger.getLogger(KafkaConsumerUtil.class);

  private final String topic;
  private final BiConsumer<K, V> consumer;
  private KafkaConsumer<K, V> kafkaConsumer;
  private volatile boolean running = true;

  public KafkaConsumerUtil(
      String groupId,
      String topic,
      String keyDeserializerClass,
      String valueDeserializerClass,
      BiConsumer<K, V> consumer) {
    LOG.info("Creating Kafka consumer for topic " + topic);
    this.topic = topic;
    this.consumer = consumer;
    Properties props = new Properties();
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializerClass);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializerClass);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, topic + "-test-consumer");

    kafkaConsumer = new KafkaConsumer<>(props);

    AtomicBoolean assigned = new AtomicBoolean(false);

    kafkaConsumer.subscribe(
        Collections.singletonList(topic),
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> collection) {}

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> collection) {
            assigned.set(true);
          }
        });

    while (!assigned.get()) {
      ConsumerRecords<K, V> poll = kafkaConsumer.poll(Duration.ofMillis(100));
      if (poll.count() > 0) {
        LOG.error(
            "Topic "
                + topic
                + " Received "
                + poll.count()
                + " records before expecting to received");
      }
    }

    start();
  }

  void start() {
    new Thread(
            () -> {
              LOG.info("Starting Kafka consumer for topic " + topic);
              while (running) {
                kafkaConsumer
                    .poll(100)
                    .forEach(
                        record -> {
                          if (running) {
                            consumer.accept(record.key(), record.value());
                          }
                        });
              }
              LOG.info("Stopping Kafka consumer for topic " + topic);
              try {
                if (kafkaConsumer != null) {
                  kafkaConsumer.unsubscribe();
                  kafkaConsumer.close(Duration.ofMillis(100));
                }
                LOG.info("Stopped Kafka consumer for topic " + topic);
                kafkaConsumer = null;
              } catch (Throwable t) {
                t.printStackTrace();
              }
            })
        .start();
  }

  void stop() {
    running = false;
  }
}
