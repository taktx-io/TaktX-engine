# Real-time Process Monitoring Frontend with WebSockets

## Overview

This document outlines the implementation plan for a real-time process monitoring frontend within the testclient-quarkus module. The application will provide Camunda Operate-like functionality but with a more dynamic, websocket-driven approach to showcase the speed and responsiveness of the TaktX BPMN engine.

## Architecture

### High-Level Architecture

```
+-----------------------------------------+
|             Browser Frontend            |
|  (HTML, JavaScript, Bootstrap, BPMN-JS) |
+-----------------------------------------+
               |        |
               |        | WebSocket Connections
               |        |
+--------------|--------|-------------------+
|              v        v                   |
| +------------------------+  +-----------+ |
| | Process Definition WS  |  | Process   | |
| | /process-definitions   |  | Instance  | |
| +------------------------+  | WS        | |
|                             |           | |
| +------------------------+  |           | |
| | Flow Node Instance WS  |  |           | |
| | /flow-node-instances   |  |           | |
| +------------------------+  +-----------+ |
|                                           |
|          Quarkus Application              |
+-----------------|-----------------------+
                  | 
         +--------v---------+
         |   TaktClient     |
         +-----------------+

```

### Components

#### 1. Backend Components

1. **WebSocket Endpoints**:
   - `ProcessDefinitionWebSocket`: Broadcasts process definition updates
   - `ProcessInstanceWebSocket`: Broadcasts process instance updates
   - `FlowNodeInstanceWebSocket`: Broadcasts flow node instance updates

2. **Update Consumers**:
   - `ProcessDefinitionUpdateConsumer`: Listens for process definition updates
   - `ProcessInstanceUpdateConsumer`: Listens for process instance updates

3. **Data Transformation**:
   - `WebSocketMessageConverter`: Converts JsonFormat.Shape.ARRAY DTOs to proper JSON objects

#### 2. Frontend Components

1. **Process Definition View**:
   - Display process definitions grouped by version
   - Subscribe to process definition updates

2. **Process Instance List**:
   - Display active process instances for selected process definition
   - Show last 100 instances with timestamps
   - Subscribe to process instance updates for selected definition

3. **BPMN Visualization**:
   - Display selected process with BPMN-JS
   - Highlight active flow nodes
   - Subscribe to flow node instance updates for selected process
   - Update visualization in real-time

## Implementation Plan

### Phase 1: Backend WebSocket Implementation

1. **Set Up WebSocket Dependencies**
   - Add Quarkus WebSocket extensions to build.gradle.kts
   - Configure WebSocket in application.properties

2. **Create Base WebSocket Infrastructure**
   - Implement WebSocket session management
   - Create subscription/unsubscription mechanisms
   - Build message format converters (for Array to JSON conversion)

3. **Implement Process Definition WebSocket**
   - Create endpoint at `/ws/process-definitions`
   - Connect to TaktClient's process definition update consumer
   - Implement grouping by version
   - Handle new connections and subscriptions

4. **Implement Process Instance WebSocket**
   - Create endpoint at `/ws/process-instances/{processDefinitionId}`
   - Connect to TaktClient's process instance update consumer
   - Implement filtering by process definition
   - Store and serve last 100 instances

5. **Implement Flow Node Instance WebSocket**
   - Create endpoint at `/ws/flow-node-instances/{processInstanceId}`
   - Connect to TaktClient for flow node instance updates
   - Filter updates by process instance

### Phase 2: Frontend Static Resources

1. **Set Up Static Resource Structure**
   - Create directory structure in `src/main/resources/META-INF/resources`
   - Set up HTML, CSS, and JavaScript files
   - Configure Bootstrap and required libraries

2. **Add Third-party Libraries**
   - Add Bootstrap
   - Add BPMN-JS library
   - Add any required utility libraries

3. **Create Base HTML Structure**
   - Create main index.html
   - Build responsive layout with sidebar and main content area
   - Implement navigation components

### Phase 3: Frontend WebSocket Clients

1. **Implement WebSocket Connection Management**
   - Create WebSocket connection handlers
   - Implement connection state management
   - Add reconnection logic

2. **Process Definition WebSocket Client**
   - Connect to `/ws/process-definitions`
   - Handle incoming messages
   - Update UI with grouped definitions
   - Implement selection functionality

3. **Process Instance WebSocket Client**
   - Connect to `/ws/process-instances/{selectedDefinitionId}`
   - Handle instance updates
   - Update instance list in UI
   - Implement selection functionality

4. **Flow Node Instance WebSocket Client**
   - Connect to `/ws/flow-node-instances/{selectedInstanceId}`
   - Handle flow node updates
   - Update BPMN visualization

### Phase 4: BPMN Visualization

1. **Integrate BPMN-JS Viewer**
   - Add library to frontend
   - Create canvas for BPMN display
   - Handle zoom and pan functionality

2. **Implement XML Retrieval**
   - Create function to get BPMN XML for selected process definition
   - Load XML into BPMN-JS viewer

3. **Implement Real-time Updates**
   - Add highlighting for active flow nodes
   - Update token positions based on websocket events
   - Animate transitions between states

### Phase 5: UI Enhancements and Integration

1. **Implement Responsive UI**
   - Optimize layout for different screen sizes
   - Add loading indicators
   - Improve user experience with feedback on actions

2. **Add Error Handling**
   - Implement WebSocket error recovery
   - Add user notifications for connection issues
   - Handle edge cases gracefully

3. **Implement Subscription Management**
   - Optimize subscription/unsubscription based on user actions
   - Ensure clean connection teardown when switching between views

## Technical Considerations

### WebSocket Efficiency
- Implement selective updates to minimize traffic
- Use binary protocols where appropriate for large datasets
- Handle reconnection gracefully

### Data Transformation
- Convert JsonFormat.Shape.ARRAY to proper JSON objects
- Minimize unnecessary data transfer
- Implement efficient client-side data structures

### Performance
- Throttle updates for high-throughput scenarios
- Batch updates when appropriate
- Implement virtual scrolling for large instance lists

### User Experience
- Ensure responsive UI even during heavy update traffic
- Provide clear visual feedback on system state
- Make subscription status visible to users

## Implementation Sequence

1. Set up project structure and dependencies
2. Implement backend WebSocket endpoints
3. Create static HTML/CSS/JS foundation
4. Implement WebSocket clients in frontend
5. Add BPMN-JS visualization
6. Connect all components
7. Add error handling and performance optimizations
8. Test with high-throughput scenarios

## Conclusion

This implementation plan provides a comprehensive approach to building a real-time process monitoring frontend using WebSockets and the TaktX engine. The focus on WebSocket communication will showcase the speed and agility of the BPMN engine while providing users with a responsive and informative monitoring experience.
