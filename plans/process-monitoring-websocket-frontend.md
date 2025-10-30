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
| +----------------------------+            |
| | REST API                   |            |
| | /api/process-definitions   |            |
| +----------------------------+            |
|                                           |
|          Quarkus Application              |
+-----------------|-----------------------+
                  | 
         +--------v---------+
         |   TaktXClient     |
         +-----------------+

```

### Components

#### 1. Backend Components

1. **REST Endpoints**:
   - `ProcessDefinitionResource`: Provides endpoints to fetch all available process definitions
   - `ProcessInstanceResource`: Provides endpoints to fetch process instances for a definition
   - `FlowNodeInstanceResource`: Provides endpoints to fetch flow node instances for a process

2. **WebSocket Endpoints**:
   - `ProcessDefinitionWebSocket`: Broadcasts process definition updates
   - `ProcessInstanceWebSocket`: Broadcasts process instance updates
   - `FlowNodeInstanceWebSocket`: Broadcasts flow node instance updates

3. **Update Consumers**:
   - `ProcessDefinitionUpdateConsumer`: Listens for process definition updates
   - `ProcessInstanceUpdateConsumer`: Listens for process instance updates

4. **Data Transformation**:
   - `WebSocketMessageConverter`: Converts JsonFormat.Shape.ARRAY DTOs to proper JSON objects

#### 2. Frontend Components

1. **Process Definition View**:
   - Initially load process definitions via REST API
   - Display process definitions grouped by version
   - Subscribe to process definition updates via WebSocket

2. **Process Instance List**:
   - Initially load process instances via REST API for selected definition
   - Display active process instances for selected process definition
   - Show last 100 instances with timestamps
   - Subscribe to process instance updates for selected definition

3. **BPMN Visualization**:
   - Display selected process with BPMN-JS
   - Highlight active flow nodes
   - Subscribe to flow node instance updates for selected process
   - Update visualization in real-time

## Implementation Plan

### Phase 1: Backend REST API Implementation

1. **Enhance Process Definition Resource**
   - Create endpoint to fetch all process definitions
   - Implement grouping by process definition ID and version
   - Ensure proper JSON serialization (convert from Array format)

2. **Create Process Instance Resource**
   - Implement endpoint to fetch process instances by definition ID
   - Add filtering capability
   - Limit to last 100 instances

3. **Create Flow Node Instance Resource**
   - Implement endpoint to fetch flow node instances by process instance ID

### Phase 2: Backend WebSocket Implementation

1. **Set Up WebSocket Infrastructure**
   - Implement WebSocket session management
   - Create subscription/unsubscription mechanisms
   - Build message format converters (for Array to JSON conversion)

2. **Implement Process Definition WebSocket**
   - Create endpoint at `/ws/process-definitions`
   - Connect to TaktXClient's process definition update consumer
   - Handle new connections and subscriptions

3. **Implement Process Instance WebSocket**
   - Create endpoint at `/ws/process-instances/{processDefinitionId}`
   - Connect to TaktXClient's process instance update consumer
   - Implement filtering by process definition
   - Store and serve last 100 instances

4. **Implement Flow Node Instance WebSocket**
   - Create endpoint at `/ws/flow-node-instances/{processInstanceId}`
   - Connect to TaktXClient for flow node instance updates
   - Filter updates by process instance

### Phase 3: Frontend Static Resources

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

### Phase 4: Frontend Implementation

1. **Implement REST API Client Functions**
   - Create functions to fetch initial process definitions
   - Create functions to fetch process instances for selected definition
   - Create functions to fetch BPMN XML for visualization

2. **Implement WebSocket Connection Management**
   - Create WebSocket connection handlers
   - Implement connection state management
   - Add reconnection logic

3. **Process Definition WebSocket Client**
   - Connect to `/ws/process-definitions`
   - Handle incoming messages
   - Update UI with grouped definitions
   - Implement selection functionality

4. **Process Instance WebSocket Client**
   - Connect to `/ws/process-instances/{selectedDefinitionId}`
   - Handle instance updates
   - Update instance list in UI
   - Implement selection functionality

5. **Flow Node Instance WebSocket Client**
   - Connect to `/ws/flow-node-instances/{selectedInstanceId}`
   - Handle flow node updates
   - Update BPMN visualization

### Phase 5: BPMN Visualization

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

### Phase 6: UI Enhancements and Integration

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

### Data Loading Strategy
- Use REST API for initial data loading
- Use WebSockets for real-time updates
- Implement appropriate caching to avoid redundant requests

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
2. Implement REST endpoints for initial data loading
3. Implement backend WebSocket endpoints
4. Create static HTML/CSS/JS foundation
5. Implement REST API clients in frontend
6. Implement WebSocket clients in frontend
7. Add BPMN-JS visualization
8. Connect all components
9. Add error handling and performance optimizations
10. Test with high-throughput scenarios

## Conclusion

This implementation plan provides a comprehensive approach to building a real-time process monitoring frontend using WebSockets and the TaktX engine. The focus on WebSocket communication will showcase the speed and agility of the BPMN engine while providing users with a responsive and informative monitoring experience. The addition of REST endpoints ensures that users can see all available process definitions immediately upon opening the application.
