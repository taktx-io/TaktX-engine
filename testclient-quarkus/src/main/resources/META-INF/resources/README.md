# TaktX Process Monitor

This is a real-time BPMN process monitoring frontend application built using HTML, JavaScript, and Bootstrap. It provides a dynamic, WebSocket-based interface for monitoring BPMN process definitions, process instances, and flow node updates.

## Features

- View process definitions grouped by version
- Monitor the latest 100 process instances for a selected definition
- Visualize BPMN diagrams with real-time flow node updates
- WebSocket-based real-time updates for responsive monitoring

## Architecture

The application is structured with three main components:

1. **Backend WebSocket Endpoints**:
   - `ProcessDefinitionWebSocket`: Provides real-time process definition updates
   - `ProcessInstanceWebSocket`: Streams process instance updates for a selected definition
   - `FlowNodeInstanceWebSocket`: Delivers flow node updates for a selected process instance

2. **REST API Endpoints**:
   - `/processdefinitions`: Returns all process definitions grouped by ID and version
   - `/processdefinitions/{id}.{version}`: Returns the BPMN XML for a specific definition

3. **Frontend Components**:
   - HTML interface with Bootstrap styling
   - JavaScript for WebSocket communication and UI updates
   - BPMN-JS viewer for diagram visualization

## Data Flow

1. On page load:
   - Initial process definitions are loaded via REST API
   - WebSocket connection is established for real-time definition updates

2. When a process definition is selected:
   - Process instances for that definition are loaded via WebSocket
   - BPMN diagram is displayed using the BPMN-JS viewer

3. When a process instance is selected:
   - WebSocket connection is established for flow node updates
   - Flow nodes are highlighted in real-time as they become active

## Usage

1. Open the application in your browser
2. Select a process definition from the left panel
3. View process instances in the middle panel
4. Select an instance to view its BPMN diagram with real-time updates

## Technical Notes

- The application uses plain HTML, JavaScript, and Bootstrap (no frameworks)
- WebSocket connections are managed dynamically based on user selections
- BPMN-JS library is used for diagram visualization
- Backend data is converted from array-formatted DTOs to standard JSON for frontend use
