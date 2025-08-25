# TaktX Process Monitoring Frontend

## Overview
This document outlines the architecture and implementation plan for a Camunda Operate-like web frontend for the TaktX engine. The frontend will display BPMN process instances with real-time updates, allowing users to monitor process execution and state changes.

## Architecture

### High-Level Architecture
```
+------------------------------------------+
|                Frontend                  |
|  (React/Angular + bpmn-js visualization) |
+--------+---------------------------+-----+
         |                           |
+--------------v------------+   +---------v---------+
| Process Instance List     |   | Process Viewer    |
| (START/STOP notifications)|   | (Selected instance)|
+--------------+------------+   +---------+---------+
         |                           |
+--------------v------------+   +---------v---------+
|   Global WebSocket        |   | Instance WebSocket |
|   /process-instances      |   | /process/{id}      |
+--------------+------------+   +---------+---------+
                |                           |
        +---v---------------------------v----+
        |        WebSocket API Service       |
        +---------------+--------------------+
                        |
              +---------v----------+    +-------------+
              |InstanceUpdateProcessor|---|  Database  |
              +---------+----------+    +------+------+
                        |                      |
              +---------v----------+    +------v------+
              |    TaktX Engine    |<---|TimescaleDB  |
              +--------------------+    +-------------+
```

### Key Components

#### 1. Database Layer - TimescaleDB

TimescaleDB offers several critical features for our high-throughput event processing:

- **Continuous Aggregates**: Auto-updated materialized views that efficiently compute and maintain process state
- **Data Retention**: Automatic policies to manage historical data storage and deletion
- **Concurrent Access**: Support for multiple writers from distributed consumers
- **Time-Series Optimization**: Specialized indexing and partitioning for time-series data

#### 2. Schema Design

```sql
-- Raw event storage hypertable
CREATE TABLE instance_updates (
  update_id SERIAL,
  process_instance_key UUID NOT NULL,
  update_type VARCHAR(50) NOT NULL,  -- 'PROCESS' or 'FLOWNODE'
  event_time TIMESTAMPTZ NOT NULL,
  process_time BIGINT NOT NULL,
  received_time TIMESTAMPTZ DEFAULT NOW(),
  update_data JSONB NOT NULL,
  process_definition_key TEXT,
  flow_node_id TEXT,
  flow_node_instance_path TEXT[]
);

-- Convert to TimescaleDB hypertable
SELECT create_hypertable('instance_updates', 'event_time');

-- Continuous aggregate for current process state
CREATE MATERIALIZED VIEW process_instance_current_state
WITH (timescaledb.continuous) AS
SELECT 
  process_instance_key,
  process_definition_key,
  MAX(process_time) as last_update_time,
  MAX(CASE WHEN update_type = 'PROCESS' THEN update_data END) as latest_process_data,
  COUNT(*) as update_count
FROM instance_updates
GROUP BY process_instance_key, process_definition_key;

-- Set up data retention (30 days for raw events)
SELECT add_retention_policy('instance_updates', INTERVAL '30 days');
```

#### 3. Backend Components

##### Instance Update Processor
- Receives updates from TaktX engine
- Batches database writes for efficiency
- Detects process lifecycle events (start/stop)
- Forwards relevant updates to WebSocket service
- Manages event buffering for high-throughput scenarios

##### WebSocket API Service
- Provides two distinct WebSocket endpoints:
  - Global endpoint for process instance lifecycle events (start/stop)
  - Instance-specific endpoint for flow node updates
- Manages client subscriptions
- Implements throttling for high-volume scenarios
- Sends only relevant updates to subscribed clients

##### REST API Layer
- Process definition browsing and metadata
- Process instance querying with filtering
- BPMN XML retrieval
- Historical data access

#### 4. Frontend Implementation

- **Technology Stack**: React/Angular with bpmn-js visualization
- **Key Components**:
  - Process Definition Browser
  - Process Instance List (with real-time updates)
  - BPMN Process Viewer (with active state highlighting)
  - Instance Details Panel

- **WebSocket Integration**:
  - Global socket for instance lifecycle notifications
  - Instance-specific socket for flow node updates
  - Efficient subscription management when switching instances

## Implementation Plan

### Phase 1: Database Foundation
1. Set up TimescaleDB with proper schema
2. Implement continuous aggregates
3. Configure data retention policies
4. Create database access layer

### Phase 2: Backend Services
1. Modify TaktClientProvider to capture instance updates
2. Implement InstanceUpdateProcessor with batching
3. Create WebSocket API service with dual endpoints
4. Develop REST API endpoints for static data

### Phase 3: Frontend Development
1. Set up frontend project structure with React/Angular
2. Implement bpmn-js integration
3. Create process instance browser component
4. Build process viewer component with WebSocket updates
5. Implement filtering and search capabilities

### Phase 4: Integration and Optimization
1. Connect all components
2. Implement caching strategies
3. Optimize WebSocket message payload sizes
4. Add monitoring and performance metrics
5. Load testing and performance tuning

## Technical Considerations

### Scalability
- Connection pooling for database
- Batch processing for high-throughput events
- WebSocket throttling for client protection
- Multiple consumers writing to shared database

### Data Management
- Efficient storage with TimescaleDB hypertables
- Automatic data retention policies
- Continuous aggregates for quick access to current state
- JSONB indexing for efficient querying

### Real-Time Updates
- Focused WebSocket updates (only what's needed)
- Process instance start/stop global notifications
- Flow node updates only for selected instance
- Client-side throttling and batching
