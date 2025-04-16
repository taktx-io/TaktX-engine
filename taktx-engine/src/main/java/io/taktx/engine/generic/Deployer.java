/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.generic;

import io.quarkus.runtime.Startup;
import io.taktx.Topics;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.license.LicenseManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;

@ApplicationScoped
@Startup(value = 10000)
@RequiredArgsConstructor
@Slf4j
public class Deployer {

  final AdminClient adminClient;
  final TaktConfiguration taktConfiguration;
  final LicenseManager licenseManager;

  @PostConstruct
  void init() {
    try {
      ListTopicsResult listTopicsResult = adminClient.listTopics();

      List<String> toCreate = new ArrayList<>();
      List<String> list = listTopicsResult.names().get().stream().toList();
      for (Topics topic : Topics.values()) {
        String name = taktConfiguration.getPrefixed(topic.getTopicName());
        if (list.contains(name)) {
          continue;
        }
        toCreate.add(name);
      }

      int partitions = taktConfiguration.getPartitions();
      int partitionLimit = licenseManager.getPartitionLimit();
      if (partitions > partitionLimit) {
        String errorMessage =
            String.format(
                "❌ LICENSE VIOLATION: Maximum allowed partitions is %d, but %d partitions configured:",
                partitionLimit, partitionLimit);

        System.out.println(errorMessage);
        System.out.println("   Shutting down due to license violation...");

        // Exit the application
        Runtime.getRuntime().halt(1);
      }

      CreateTopicsResult topics =
          adminClient.createTopics(
              toCreate.stream()
                  .map(
                      topic ->
                          new NewTopic(
                              topic, partitions, (short) taktConfiguration.getReplicationFactor()))
                  .toList());

      topics.all().get();

      validateTopicAccordingLicense();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  private void validateTopicAccordingLicense() throws ExecutionException, InterruptedException {
    Map<String, Integer> topicPartitions = checkKafkaTopicPartitions();

    int maxPartitionsUsed =
        topicPartitions.values().stream().mapToInt(Integer::intValue).max().orElse(0);

    System.out.println("   Kafka topics configuration:");
    topicPartitions.forEach(
        (topic, partitions) ->
            System.out.println("   - " + topic + ": " + partitions + " partitions"));

    int partitionLimit = licenseManager.getPartitionLimit();
    if (maxPartitionsUsed > partitionLimit) {
      String errorMessage =
          String.format(
              "❌ LICENSE VIOLATION: Maximum allowed partitions is %d, but %d partitions found in topic(s):",
              partitionLimit, maxPartitionsUsed);

      System.out.println(errorMessage);

      System.out.println("   Shutting down due to license violation...");
      // Also log the error
      log.error(errorMessage);
      topicPartitions.entrySet().stream()
          .filter(entry -> entry.getValue() > partitionLimit)
          .forEach(
              entry ->
                  log.error(
                      "Topic {} has {} partitions, exceeding license limit of {}",
                      entry.getKey(),
                      entry.getValue(),
                      partitionLimit));

      // Exit the application
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Checks all Kafka topics defined in configuration and returns their partition counts
   *
   * @return Map of topic names to their partition counts
   * @throws ExecutionException If there's an error communicating with Kafka
   * @throws InterruptedException If the operation is interrupted
   */
  private Map<String, Integer> checkKafkaTopicPartitions()
      throws ExecutionException, InterruptedException {
    // Parse topics from configuration
    // Get topic information from Kafka
    List<String> prefixedTopics =
        Arrays.stream(Topics.values())
            .map(topic -> taktConfiguration.getPrefixed(topic.getTopicName()))
            .toList();
    DescribeTopicsResult topicsResult = adminClient.describeTopics(prefixedTopics);
    Map<String, TopicDescription> topicDescriptions = topicsResult.allTopicNames().get();

    // Return map of topic names to partition counts
    return topicDescriptions.entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().partitions().size()));
  }

  public void createTopicsForProcessDefinition(String processDefinitionId) {

    try {
      List<String> prefixedTopics =
          List.of(taktConfiguration.getPrefixed("external-task-trigger-") + processDefinitionId);

      Set<String> strings = adminClient.listTopics().names().get();

      int partitions = taktConfiguration.getPartitions();
      log.info(
          "Creating topics for process definition {}: {}", processDefinitionId, prefixedTopics);
      CreateTopicsResult topics =
          adminClient.createTopics(
              prefixedTopics.stream()
                  .map(
                      topic ->
                          new NewTopic(
                              topic, partitions, (short) taktConfiguration.getReplicationFactor()))
                  .toList());
      topics.all().get();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
