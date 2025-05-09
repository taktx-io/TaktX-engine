# Topic-Per-Worker Architecture Implementation Plan

## Overview

This document outlines the implementation plan for a distributed topic administration architecture in TaktX. The architecture enables a topic-per-service-task approach where job workers register their capabilities, topics are created based on these registrations, and workers only start processing when their required topics are available. It also ensures that system-wide fixed topics defined in the `Topics` enum are properly managed.

## Design Principles

1. **Worker-First Approach**: Job workers drive topic creation by registering their capabilities
2. **Distributed Event-Driven Design**: Topic status shared via Kafka events
3. **Flexible Deployment**: Support for both automatic and manual topic creation workflows
4. **Integration Test Support**: Easy topic creation for testing scenarios
5. **Unified Topic Management**: Consistent management of both dynamic worker topics and fixed system topics

## Implementation Components

The implementation is distributed across three modules:

1. **taktx-shared**: Core models and events
2. **taktx-client**: Job worker registration and topic management
3. **taktx-admin**: Admin module for topic monitoring and management (new)

## Direct Implementation Approach

Since the software is not yet in production, we can implement all changes at once with a direct approach.

## Module 1: taktx-shared

### Core Models and Events

#### 1. Enhance ExternalTaskMetaDTO

```java
@Data
@EqualsAndHashCode
@ToString(callSuper = true)
@NoArgsConstructor
public class ExternalTaskMetaDTO {
  private String externalTaskId;  // This is the job type and serves as the base for the topic name
  private int nrPartitions;
  private String topicStatus;     // Current status of the topic
}
```

#### 2. Create TopicStatus Enum

```java
public enum TopicStatus {
    REQUESTED,   // Topic has been requested but not yet processed
    PENDING,     // Topic creation is in progress
    CREATED,     // Topic has been successfully created
    ERROR        // Topic creation failed
}
```

#### 3. Add Topic Registration Model

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicRegistration {
    private String topicName;     // The full topic name (with prefix if applicable)
    private boolean fixedTopic;   // Indicates if this is a fixed system topic
    private int partitions;
    private short replicationFactor;
    private TopicStatus status;
    private Map<String, String> configs;
    private LocalDateTime requestedTime;
    private LocalDateTime createdTime;
    private String errorMessage;
}
```

#### 4. Update Topics Enum

```java
@Getter
public enum Topics {
  // Existing topics...
  EXTERNAL_TASK_META_TOPIC("external-task-meta", "earliest"),
  TOPIC_ADMIN_TOPIC("topic-admin", "earliest"); // NEW
  
  // Rest of the class...
  
  // Helper method to get all topic names
  public static List<String> getAllTopicNames() {
    return Arrays.stream(Topics.values())
        .map(Topics::getTopicName)
        .collect(Collectors.toList());
  }
}
```

## Module 2: taktx-client

### Topic Management

#### 1. Create TopicManager Interface and Implementation

```java
public interface TopicManager {
    CompletableFuture<Void> ensureTopicExists(String jobType, int partitions);
    CompletableFuture<Void> ensureFixedTopicExists(Topics topic, int partitions);
    CompletableFuture<Boolean> topicExists(String topicName);
    CompletableFuture<Set<String>> listTopics();
}

@Slf4j
public class KafkaTopicManager implements TopicManager {
    private final AdminClient adminClient;
    private final TaktPropertiesHelper taktPropertiesHelper;
    
    public KafkaTopicManager(TaktPropertiesHelper taktPropertiesHelper, Properties kafkaProperties) {
        this.taktPropertiesHelper = taktPropertiesHelper;
        Properties adminProps = new Properties();
        adminProps.putAll(kafkaProperties);
        this.adminClient = AdminClient.create(adminProps);
    }
    
    @Override
    public CompletableFuture<Void> ensureTopicExists(String jobType, int partitions) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String topicName = taktPropertiesHelper.getPrefixedTopicName(jobType);
        
        ensureTopicExistsInternal(topicName, partitions, future);
        
        return future;
    }
    
    @Override
    public CompletableFuture<Void> ensureFixedTopicExists(Topics topic, int partitions) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String topicName = topic.getTopicName();
        
        ensureTopicExistsInternal(topicName, partitions, future);
        
        return future;
    }
    
    private void ensureTopicExistsInternal(String topicName, int partitions, CompletableFuture<Void> future) {
        topicExists(topicName).thenAccept(exists -> {
            if (exists) {
                future.complete(null);
            } else {
                NewTopic newTopic = new NewTopic(topicName, partitions, (short) 1);
                adminClient.createTopics(Collections.singleton(newTopic))
                    .all()
                    .whenComplete((v, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        } else {
                            future.complete(null);
                        }
                    });
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> topicExists(String topicName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        adminClient.listTopics()
            .names()
            .whenComplete((names, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    future.complete(names.contains(topicName));
                }
            });
            
        return future;
    }
    
    @Override
    public CompletableFuture<Set<String>> listTopics() {
        CompletableFuture<Set<String>> future = new CompletableFuture<>();
        
        adminClient.listTopics()
            .names()
            .whenComplete((names, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    future.complete(names);
                }
            });
            
        return future;
    }
    
    public void close() {
        if (adminClient != null) {
            adminClient.close();
        }
    }
}
```

#### 2. Create Worker Registration Component

```java
@Slf4j
public class TopicRegistrar {
    private final KafkaProducer<String, ExternalTaskMetaDTO> producer;
    private final TaktPropertiesHelper taktPropertiesHelper;
    private final TopicManager topicManager;
    private final boolean autoCreateTopics;
    private final int defaultPartitions;
    
    public TopicRegistrar(TaktPropertiesHelper taktPropertiesHelper, TopicManager topicManager) {
        this.taktPropertiesHelper = taktPropertiesHelper;
        this.topicManager = topicManager;
        this.autoCreateTopics = taktPropertiesHelper.getBoolean("takt.topics.auto-create", true);
        this.defaultPartitions = taktPropertiesHelper.getInteger("takt.topics.default-partitions", 1);
        
        Properties props = taktPropertiesHelper.getKafkaProducerProperties(
            StringSerializer.class, 
            ExternalTaskMetaJsonSerializer.class);
        this.producer = new KafkaProducer<>(props);
    }
    
    public void registerWorkerJobTypes(Set<String> jobTypes, int partitions) {
        for (String jobType : jobTypes) {
            ExternalTaskMetaDTO metaDTO = new ExternalTaskMetaDTO();
            metaDTO.setExternalTaskId(jobType);
            metaDTO.setNrPartitions(partitions);
            metaDTO.setTopicStatus(TopicStatus.REQUESTED.name());
            
            producer.send(new ProducerRecord<>(
                Topics.EXTERNAL_TASK_META_TOPIC.getTopicName(),
                jobType,
                metaDTO
            ));
            
            if (autoCreateTopics) {
                topicManager.ensureTopicExists(jobType, partitions);
            }
        }
    }
    
    public void registerFixedTopics() {
        for (Topics topic : Topics.values()) {
            if (autoCreateTopics) {
                topicManager.ensureFixedTopicExists(topic, defaultPartitions)
                    .thenAccept(v -> 
                        log.info("Fixed topic {} registered and created", topic.getTopicName()))
                    .exceptionally(ex -> {
                        log.error("Failed to register fixed topic {}: {}", 
                            topic.getTopicName(), ex.getMessage());
                        return null;
                    });
            }
        }
    }
    
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
```

#### 3. Update ExternalTaskMetaMonitor Implementation

```java
@Slf4j
public class ExternalTaskMetaMonitor implements ExternalTaskMetaConsumer {
    private final Map<String, ExternalTaskMetaDTO> metaMap = new ConcurrentHashMap<>();
    private final Set<String> jobIdsToMonitor;
    private final Consumer<Void> callback;
    private final long waitTimeoutMs;
    private final Set<String> fixedTopicNames;
    private final Map<String, TopicStatus> fixedTopicsStatus = new ConcurrentHashMap<>();
    private boolean allTopicsReady = false;
    
    public ExternalTaskMetaMonitor(
            Set<String> jobIdsToMonitor, 
            Consumer<Void> callback, 
            long waitTimeoutMs) {
        this.jobIdsToMonitor = jobIdsToMonitor;
        this.callback = callback;
        this.waitTimeoutMs = waitTimeoutMs;
        this.fixedTopicNames = new HashSet<>(Topics.getAllTopicNames());
        
        // Initialize fixed topics to REQUESTED state
        for (String topicName : fixedTopicNames) {
            fixedTopicsStatus.put(topicName, TopicStatus.REQUESTED);
        }
        
        // Start monitor
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(this::checkTimeoutAndNotify, waitTimeoutMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void accept(ConsumerRecord<String, ExternalTaskMetaDTO> record) {
        ExternalTaskMetaDTO meta = record.value();
        if (meta != null) {
            String jobId = meta.getExternalTaskId();
            metaMap.put(jobId, meta);
            
            // Check if this is an update for a fixed topic
            // For fixed topics, the key will be the topic name
            String topicName = record.key();
            if (fixedTopicNames.contains(topicName)) {
                fixedTopicsStatus.put(topicName, 
                    TopicStatus.valueOf(meta.getTopicStatus()));
            }
            
            checkTopicsAndNotify();
        }
    }
    
    private synchronized void checkTopicsAndNotify() {
        if (allTopicsReady) {
            return; // Already notified
        }
        
        boolean allWorkerTopicsReady = jobIdsToMonitor.stream()
            .map(metaMap::get)
            .filter(Objects::nonNull)
            .map(ExternalTaskMetaDTO::getTopicStatus)
            .allMatch(TopicStatus.CREATED.name()::equals);
            
        boolean allFixedTopicsReady = fixedTopicsStatus.values().stream()
            .allMatch(status -> status == TopicStatus.CREATED);
            
        if (allWorkerTopicsReady && allFixedTopicsReady) {
            allTopicsReady = true;
            callback.accept(null);
        }
    }
    
    private void checkTimeoutAndNotify() {
        if (!allTopicsReady) {
            log.warn("Not all topics are ready after timeout. Proceeding anyway.");
            callback.accept(null);
        }
    }
}
```

#### 4. Update TaktClient to Initialize Topic Management

```java
public TaktClient(...) {
    // Existing initialization...
    this.topicManager = new KafkaTopicManager(taktPropertiesHelper, kafkaProperties);
    this.topicRegistrar = new TopicRegistrar(taktPropertiesHelper, topicManager);
    
    // Register fixed topics at initialization
    this.topicRegistrar.registerFixedTopics();
    
    // Rest of initialization...
}
```

## Module 3: taktx-admin

### Topic Administration

#### 1. Create TopicRegistryService

```java
@Service
@Slf4j
public class TopicRegistryService {
    private final Map<String, TopicRegistration> topicRegistry = new ConcurrentHashMap<>();
    private final TopicCreationService topicCreationService;
    private final KafkaTemplate<String, ExternalTaskMetaDTO> kafkaTemplate;
    private final TaktPropertiesHelper taktPropertiesHelper;
    private final boolean autoCreateTopics;
    
    public TopicRegistryService(
            TopicCreationService topicCreationService,
            KafkaTemplate<String, ExternalTaskMetaDTO> kafkaTemplate,
            TaktPropertiesHelper taktPropertiesHelper) {
        this.topicCreationService = topicCreationService;
        this.kafkaTemplate = kafkaTemplate;
        this.taktPropertiesHelper = taktPropertiesHelper;
        this.autoCreateTopics = taktPropertiesHelper.getBoolean("takt.topics.auto-create", true);
        
        // Register fixed topics at startup
        registerFixedTopics();
    }
    
    @KafkaListener(topics = "#{T(io.taktx.Topics).EXTERNAL_TASK_META_TOPIC.getTopicName()}")
    public void processTopicRegistration(ExternalTaskMetaDTO metaDTO) {
        String jobType = metaDTO.getExternalTaskId();
        String topicName = taktPropertiesHelper.getPrefixedTopicName(jobType);
        
        TopicRegistration registration = topicRegistry.computeIfAbsent(
            topicName,
            k -> TopicRegistration.builder()
                .topicName(topicName)
                .fixedTopic(false)
                .partitions(metaDTO.getNrPartitions())
                .replicationFactor((short) 1)
                .status(TopicStatus.REQUESTED)
                .requestedTime(LocalDateTime.now())
                .build()
        );
        
        if (autoCreateTopics) {
            createTopic(registration);
        }
    }
    
    public void registerFixedTopics() {
        for (Topics topic : Topics.values()) {
            String topicName = topic.getTopicName();
            
            TopicRegistration registration = TopicRegistration.builder()
                .topicName(topicName)
                .fixedTopic(true)
                .partitions(taktPropertiesHelper.getInteger("takt.topics.default-partitions", 1))
                .replicationFactor((short) 1)
                .status(TopicStatus.REQUESTED)
                .requestedTime(LocalDateTime.now())
                .build();
                
            topicRegistry.put(topicName, registration);
            
            if (autoCreateTopics) {
                createTopic(registration);
            }
        }
    }
    
    public void createTopic(TopicRegistration registration) {
        registration.setStatus(TopicStatus.PENDING);
        
        topicCreationService.createTopic(
            registration.getTopicName(),
            registration.getPartitions(),
            registration.getReplicationFactor()
        ).thenAccept(success -> {
            if (success) {
                registration.setStatus(TopicStatus.CREATED);
                registration.setCreatedTime(LocalDateTime.now());
            } else {
                registration.setStatus(TopicStatus.ERROR);
                registration.setErrorMessage("Failed to create topic");
            }
            
            // Publish status update
            if (registration.isFixedTopic()) {
                publishFixedTopicStatus(registration);
            } else {
                publishWorkerTopicStatus(registration);
            }
        }).exceptionally(ex -> {
            registration.setStatus(TopicStatus.ERROR);
            registration.setErrorMessage(ex.getMessage());
            
            if (registration.isFixedTopic()) {
                publishFixedTopicStatus(registration);
            } else {
                publishWorkerTopicStatus(registration);
            }
            
            return null;
        });
    }
    
    private void publishWorkerTopicStatus(TopicRegistration registration) {
        // Extract the job type from the topic name (reverse of prefixing operation)
        String jobType = taktPropertiesHelper.getJobTypeFromTopicName(registration.getTopicName());
        
        ExternalTaskMetaDTO metaDTO = new ExternalTaskMetaDTO();
        metaDTO.setExternalTaskId(jobType);
        metaDTO.setNrPartitions(registration.getPartitions());
        metaDTO.setTopicStatus(registration.getStatus().name());
        
        kafkaTemplate.send(
            Topics.EXTERNAL_TASK_META_TOPIC.getTopicName(),
            jobType,
            metaDTO
        );
    }
    
    private void publishFixedTopicStatus(TopicRegistration registration) {
        String topicName = registration.getTopicName();
        
        ExternalTaskMetaDTO metaDTO = new ExternalTaskMetaDTO();
        metaDTO.setExternalTaskId(topicName);  // For fixed topics, use topic name as the ID
        metaDTO.setNrPartitions(registration.getPartitions());
        metaDTO.setTopicStatus(registration.getStatus().name());
        
        kafkaTemplate.send(
            Topics.EXTERNAL_TASK_META_TOPIC.getTopicName(),
            topicName,  // Using topicName as key for fixed topics
            metaDTO
        );
    }
    
    public List<TopicRegistration> getAllTopicRegistrations() {
        return new ArrayList<>(topicRegistry.values());
    }
    
    public List<TopicRegistration> getFixedTopicRegistrations() {
        return topicRegistry.values().stream()
            .filter(TopicRegistration::isFixedTopic)
            .collect(Collectors.toList());
    }
    
    public List<TopicRegistration> getWorkerTopicRegistrations() {
        return topicRegistry.values().stream()
            .filter(r -> !r.isFixedTopic())
            .collect(Collectors.toList());
    }
    
    public Optional<TopicRegistration> getTopicRegistration(String topicName) {
        return Optional.ofNullable(topicRegistry.get(topicName));
    }
}
```

#### 2. Add Helper Method to TaktPropertiesHelper

```java
public String getJobTypeFromTopicName(String topicName) {
    String prefix = getProperty("takt.topics.prefix", "") + ".";
    if (topicName.startsWith(prefix)) {
        return topicName.substring(prefix.length());
    }
    return topicName; // If no prefix found, assume the topic name is the job type
}
```

#### 3. Enhance TopicCreationService

```java
@Service
@Slf4j
public class TopicCreationService {
    private final AdminClient adminClient;
    
    public TopicCreationService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(props);
    }
    
    public CompletableFuture<Boolean> createTopic(
            String topicName, 
            int partitions, 
            short replicationFactor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        topicExists(topicName).thenAccept(exists -> {
            if (exists) {
                log.info("Topic {} already exists", topicName);
                future.complete(true);
                return;
            }
            
            NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
            adminClient.createTopics(Collections.singleton(newTopic))
                .all()
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("Failed to create topic {}: {}", topicName, ex.getMessage());
                        future.complete(false);
                    } else {
                        log.info("Successfully created topic {}", topicName);
                        future.complete(true);
                    }
                });
        });
        
        return future;
    }
    
    public CompletableFuture<Boolean> topicExists(String topicName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        adminClient.listTopics()
            .names()
            .whenComplete((names, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    future.complete(names.contains(topicName));
                }
            });
            
        return future;
    }
    
    public CompletableFuture<Set<String>> listTopics() {
        CompletableFuture<Set<String>> future = new CompletableFuture<>();
        
        adminClient.listTopics()
            .names()
            .whenComplete((names, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    future.complete(names);
                }
            });
            
        return future;
    }
}
```

#### 4. Update TopicAdminController

```java
@RestController
@RequestMapping("/api/topics")
@Slf4j
public class TopicAdminController {
    private final TopicRegistryService registryService;
    private final TopicCreationService creationService;
    
    public TopicAdminController(
            TopicRegistryService registryService,
            TopicCreationService creationService) {
        this.registryService = registryService;
        this.creationService = creationService;
    }
    
    @GetMapping
    public ResponseEntity<List<TopicRegistration>> getAllTopics() {
        return ResponseEntity.ok(registryService.getAllTopicRegistrations());
    }
    
    @GetMapping("/fixed")
    public ResponseEntity<List<TopicRegistration>> getFixedTopics() {
        return ResponseEntity.ok(registryService.getFixedTopicRegistrations());
    }
    
    @GetMapping("/worker")
    public ResponseEntity<List<TopicRegistration>> getWorkerTopics() {
        return ResponseEntity.ok(registryService.getWorkerTopicRegistrations());
    }
    
    @GetMapping("/{topicName}")
    public ResponseEntity<TopicRegistration> getTopicByName(@PathVariable String topicName) {
        return registryService.getTopicRegistration(topicName)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{topicName}")
    public ResponseEntity<Void> createTopic(
            @PathVariable String topicName,
            @RequestParam(defaultValue = "1") int partitions,
            @RequestParam(defaultValue = "1") short replicationFactor) {
        
        registryService.getTopicRegistration(topicName)
            .ifPresent(registryService::createTopic);
            
        return ResponseEntity.accepted().build();
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshFixedTopics() {
        registryService.registerFixedTopics();
        return ResponseEntity.accepted().build();
    }
}
```

## Configuration Options

Configuration options to control topic management behavior:

```properties
# Enable/disable automatic topic creation
takt.topics.auto-create=true

# Default number of partitions for topics
takt.topics.default-partitions=1

# Default replication factor
takt.topics.replication-factor=1

# Timeout (in milliseconds) to wait for topics to be ready
takt.topics.wait-timeout-ms=30000

# Optional prefix for worker topic names
takt.topics.prefix=
```

## Testing Support

For integration testing, the TopicRegistrar can be configured to automatically create all required topics:

```java
@Bean
@Profile("test")
public TopicRegistrar testTopicRegistrar(TaktPropertiesHelper helper, TopicManager topicManager) {
    TopicRegistrar registrar = new TopicRegistrar(helper, topicManager);
    // Ensure all fixed topics are created for testing
    registrar.registerFixedTopics();
    return registrar;
}
```

This implementation provides a robust, distributed approach to topic administration that supports both automatic topic creation for development/testing and manual administration for production environments. By simplifying the relationship between job types and topic names, we make the system more intuitive while maintaining all necessary functionality.

The design ensures that job workers only start processing when their required topics are available, preventing errors during startup and runtime execution. Additionally, the system now properly handles both dynamic worker topics and fixed system topics defined in the Topics enum.