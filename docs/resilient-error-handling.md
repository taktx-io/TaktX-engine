# Resilient Error Handling for ExternalTaskTriggerTopicConsumer

## Summary of Changes

The `ExternalTaskTriggerTopicConsumer` has been refactored to implement **resilient error handling** that distinguishes between fatal infrastructure errors and recoverable business logic errors. The consumer no longer stops on every exception, but instead handles different error types appropriately.

---

## Problem Analysis

### Previous Behavior ❌

The original implementation had a single broad `catch (Throwable t)` block that would:
1. **Stop the entire consumer thread** on ANY exception
2. Close Kafka connections
3. Require manual restart
4. Potentially lose or duplicate messages

**Critical Issues:**
- User code exceptions in `acceptBatch()` would kill the consumer
- No distinction between infrastructure vs business logic errors
- Acknowledgment happened before knowing if processing succeeded
- No retry or error recovery mechanism

---

## New Design ✅

### 1. **Separated Error Handling**

#### Fatal Errors (Stop Consumer)
```java
try {
  records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
} catch (Exception e) {
  // Kafka infrastructure error - fatal, need to stop consumer
  log.error("Fatal Kafka polling error, stopping consumer", e);
  running = false;
  break;
}
```

**Examples:**
- Network connectivity loss
- Kafka broker unavailable
- Authentication failures
- Serialization errors

**Action:** Stop consumer, close connection, let orchestration layer restart

#### Non-Fatal Errors (Continue Processing)
```java
boolean batchProcessedSuccessfully = processBatchWithErrorHandling(...);
if (!batchProcessedSuccessfully) {
  log.warn("Batch processing failed, not acknowledging messages");
  // Don't acknowledge - messages will be reprocessed
}
// Consumer continues running!
```

**Examples:**
- User code throws exception in `acceptBatch()`
- Business logic validation failures
- Temporary downstream service unavailability
- Data processing errors

**Action:** Log error, don't acknowledge message, continue with next batch

---

### 2. **Safe Acknowledgment Strategy**

#### Key Principle: **Only acknowledge after successful processing**

```java
// Process batch first
boolean batchProcessedSuccessfully = processBatchWithErrorHandling(...);

// Acknowledge ONLY if successful
if (batchProcessedSuccessfully) {
  try {
    acknowledgeRecords(consumer, jobRecords, ackStrategy);
  } catch (Exception e) {
    log.error("Error acknowledging, messages will be reprocessed", e);
  }
} else {
  // Don't acknowledge - Kafka will redeliver
  if (ackStrategy == AckStrategy.IMPLICIT) {
    // Seek back to prevent auto-commit
    consumer.seek(topicPartition, record.offset());
  }
}
```

#### Acknowledgment Strategies

##### **IMPLICIT (Default Kafka auto-commit)**
- **Success:** Auto-commit happens after `auto.commit.interval.ms`
- **Failure:** Seek back to failed offset to prevent auto-commit
- **Risk:** If process crashes between processing and auto-commit, messages reprocessed
- **Best for:** Fire-and-forget scenarios, idempotent processing

##### **EXPLICIT_BATCH**
- **Success:** `consumer.commitSync()` after entire batch succeeds
- **Failure:** No commit, entire batch reprocessed
- **Risk:** If one message fails, all messages in batch reprocessed
- **Best for:** Batch processing where order matters, transactional consistency

##### **EXPLICIT_MESSAGE**
- **Success:** Commit each message individually after processing
- **Failure:** Only unprocessed messages remain uncommitted
- **Risk:** Highest commit overhead
- **Best for:** Critical messages that cannot be lost, maximum at-least-once guarantees

---

### 3. **Threading Strategy Error Handling**

#### **SINGLE_THREAD**
```java
try {
  consumer.acceptBatch(batch);
  return true; // Success
} catch (Exception e) {
  log.error("Error processing batch", e);
  return false; // Failure - don't acknowledge
}
```
- Simple error handling
- Synchronous processing
- Clear success/failure state

#### **VIRTUAL_THREAD_WAIT**
```java
List<CompletableFuture<Boolean>> futures = new ArrayList<>();
for (ExternalTaskTriggerDTO dto : batch) {
  futures.add(CompletableFuture.supplyAsync(() -> {
    try {
      consumer.acceptBatch(List.of(dto));
      return true;
    } catch (Exception e) {
      log.error("Error processing message {}", dto.getExternalTaskId(), e);
      return false;
    }
  }, virtualThreadExecutor));
}

// Wait for all and check if ALL succeeded
boolean allSucceeded = futures.stream().allMatch(f -> f.getNow(false));
return allSucceeded; // Only acknowledge if all succeeded
```
- Parallel processing with error tracking
- **Batch considered failed if ANY message fails**
- All messages reprocessed on failure (safe but may cause duplicates)

#### **VIRTUAL_THREAD_FIRE_AND_FORGET**
```java
for (ExternalTaskTriggerDTO dto : batch) {
  CompletableFuture.runAsync(() -> {
    try {
      consumer.acceptBatch(List.of(dto));
    } catch (Exception e) {
      log.error("Error processing message (may be lost)", e);
    }
  }, virtualThreadExecutor);
}
return true; // Assume success immediately
```
- **⚠️ WARNING:** Cannot track success, messages may be lost on error
- Should only use with `IMPLICIT` acknowledgment
- Best for non-critical notifications

---

## Error Recovery Flow

### Scenario 1: User Code Exception in acceptBatch()

```
1. Consumer polls messages from Kafka
2. Groups by jobId
3. Calls processBatchWithErrorHandling()
   └─> User code throws exception
4. Exception caught, logged
5. Returns false (batch failed)
6. Acknowledgment SKIPPED
7. For IMPLICIT: seek back to prevent auto-commit
8. Consumer loop CONTINUES
9. Next poll will redeliver failed messages
```

**Result:** ✅ Consumer stays alive, message redelivered

### Scenario 2: Kafka Infrastructure Error

```
1. Consumer attempts to poll
2. Kafka throws connectivity exception
3. Exception caught in specific poll try/catch
4. Sets running = false
5. Breaks out of loop
6. Finally block cleans up consumer
7. Consumer thread exits
```

**Result:** ✅ Consumer stops gracefully, orchestration layer can restart

### Scenario 3: Acknowledgment Error

```
1. Batch processes successfully
2. Calls acknowledgeRecords()
3. Kafka commit throws exception
4. Exception caught and logged
5. Consumer loop CONTINUES
6. Next poll will redeliver (duplicate processing)
```

**Result:** ✅ Consumer stays alive, at-least-once delivery preserved

---

## Best Practices & Recommendations

### ✅ DO's

1. **Make your handlers idempotent**
   - Expect duplicate message delivery
   - Use unique IDs to detect duplicates
   
2. **Use EXPLICIT_BATCH for critical workflows**
   - Ensures no message loss
   - Clear success/failure state

3. **Use VIRTUAL_THREAD_WAIT for parallelism**
   - Good balance of performance and safety
   - All messages tracked

4. **Implement retry logic in your handler**
   - Transient errors should retry with backoff
   - Permanent errors should fail fast

5. **Monitor failed message patterns**
   - Set up alerts for repeated failures
   - Implement circuit breaker if needed

### ❌ DON'Ts

1. **Don't use VIRTUAL_THREAD_FIRE_AND_FORGET with EXPLICIT acknowledgment**
   - Messages will be lost on error
   - No way to track success

2. **Don't throw exceptions for business validation**
   - Handle validation errors gracefully
   - Return error response instead

3. **Don't assume messages are processed exactly once**
   - At-least-once delivery semantics
   - Design for duplicate handling

4. **Don't process non-idempotent operations without deduplication**
   - Financial transactions
   - Inventory updates
   - State mutations

---

## Configuration Examples

### High Reliability (No Message Loss)
```properties
taktx.external-task.ack-strategy=EXPLICIT_BATCH
taktx.external-task.threading-strategy=VIRTUAL_THREAD_WAIT
taktx.external-task.consumer-threads=4
taktx.external-task.max-poll-records=10
```

### High Throughput (Accept Some Duplicates)
```properties
taktx.external-task.ack-strategy=IMPLICIT
taktx.external-task.threading-strategy=VIRTUAL_THREAD_WAIT
taktx.external-task.consumer-threads=8
taktx.external-task.max-poll-records=100
```

### Low Latency (Fire and Forget)
```properties
taktx.external-task.ack-strategy=IMPLICIT
taktx.external-task.threading-strategy=VIRTUAL_THREAD_FIRE_AND_FORGET
taktx.external-task.consumer-threads=2
taktx.external-task.max-poll-records=50
```

---

## Testing Recommendations

### Test Cases to Implement

1. **User Code Exception**
   - Verify consumer stays alive
   - Verify message redelivery
   - Verify other messages continue processing

2. **Kafka Connectivity Loss**
   - Verify consumer stops gracefully
   - Verify resources cleaned up
   - Verify restart works

3. **Partial Batch Failure (VIRTUAL_THREAD_WAIT)**
   - Verify all messages reprocessed
   - Verify no message loss

4. **Acknowledgment Failure**
   - Verify consumer continues
   - Verify duplicate handling

---

## Migration Guide

If you're upgrading from the previous version:

1. **No code changes required** - fully backward compatible
2. **Improved reliability** - consumers no longer crash on user code errors
3. **Better observability** - clearer error logging
4. **Review your handlers** - ensure they're idempotent for redelivery

---

## Future Enhancements

Consider implementing:

1. **Dead Letter Queue (DLQ)**
   - After N retries, send to DLQ topic
   - Manual inspection and reprocessing

2. **Circuit Breaker**
   - Stop processing if error rate exceeds threshold
   - Automatic recovery after cooldown

3. **Retry with Backoff**
   - Exponential backoff for transient errors
   - Configurable retry limits

4. **Metrics & Monitoring**
   - Track success/failure rates
   - Alert on repeated failures
   - Monitor consumer lag

5. **Per-JobId Configuration**
   - Different strategies for different job types
   - Critical vs best-effort processing

---

## Related Files

- **Implementation:** `taktx-client/src/main/java/io/taktx/client/ExternalTaskTriggerTopicConsumer.java`
- **Annotations:** `taktx-client/src/main/java/io/taktx/client/annotation/AckStrategy.java`
- **Threading:** `taktx-client/src/main/java/io/taktx/client/annotation/ThreadingStrategy.java`

