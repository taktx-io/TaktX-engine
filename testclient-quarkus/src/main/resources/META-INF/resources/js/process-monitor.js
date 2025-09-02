/**
 * TaktX Process Monitor Frontend
 * 
 * This script handles the real-time process monitoring frontend functionality,
 * including WebSocket communication and UI updates.
 */

// Global state
const state = {
    processDefinitions: {},  // Grouped by process definition ID
    currentDefinitionId: null,
    currentDefinitionVersion: null,
    currentInstanceId: null,
    connections: {
        definitions: null,
        instances: null,
        flowNodes: null
    },
    bpmnViewer: null,
    processInstances: {}  // Stores process instance data by definition ID
};

// Connection status indicators
const ConnectionStatus = {
    CONNECTED: 'connected',
    DISCONNECTED: 'disconnected',
    CONNECTING: 'connecting'
};

// Initialize the application when DOM is fully loaded
document.addEventListener('DOMContentLoaded', function() {
    initializeUI();
    loadProcessDefinitions();
    initializeBpmnViewer();
    setupProcessDefinitionWebSocket();
});

// Initialize UI components
function initializeUI() {
    updateConnectionStatus('definitions', ConnectionStatus.DISCONNECTED);
    
    // Event delegation for process definition clicks
    document.getElementById('processDefinitionsList').addEventListener('click', function(e) {
        const definitionElement = e.target.closest('.process-definition');
        if (definitionElement) {
            const definitionId = definitionElement.getAttribute('data-definition-id');
            const version = parseInt(definitionElement.getAttribute('data-version'), 10);
            selectProcessDefinition(definitionId, version);
        }
    });
    
    // Event delegation for process instance clicks
    document.getElementById('processInstancesList').addEventListener('click', function(e) {
        const instanceElement = e.target.closest('.process-instance');
        if (instanceElement) {
            const instanceId = instanceElement.getAttribute('data-instance-id');
            selectProcessInstance(instanceId);
        }
    });
}

/**
 * REST API Functions
 */

// Load process definitions via REST API
function loadProcessDefinitions() {
    fetch('/processdefinitions')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch process definitions');
            }
            return response.json();
        })
        .then(data => {
            state.processDefinitions = data;
            renderProcessDefinitions();
        })
        .catch(error => {
            console.error('Error loading process definitions:', error);
            document.getElementById('processDefinitionsList').innerHTML = 
                `<div class="p-3 text-danger">Failed to load process definitions: ${error.message}</div>`;
        });
}

// Fetch BPMN XML for a process definition
function loadBpmnXml(definitionId, version) {
    return fetch(`/processdefinitions/${definitionId}.${version}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch BPMN XML');
            }
            return response.text();
        });
}

/**
 * WebSocket Connection Management
 */

// Set up WebSocket connection for process definitions
function setupProcessDefinitionWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/process-definitions`;
    
    updateConnectionStatus('definitions', ConnectionStatus.CONNECTING);
    
    const socket = new WebSocket(wsUrl);
    state.connections.definitions = socket;
    
    socket.onopen = function() {
        console.log('Process definition WebSocket connection established');
        updateConnectionStatus('definitions', ConnectionStatus.CONNECTED);
    };
    
    socket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        handleProcessDefinitionMessage(message);
    };
    
    socket.onclose = function() {
        console.log('Process definition WebSocket connection closed');
        updateConnectionStatus('definitions', ConnectionStatus.DISCONNECTED);
        
        // Attempt to reconnect after a delay
        setTimeout(setupProcessDefinitionWebSocket, 3000);
    };
    
    socket.onerror = function(error) {
        console.error('Process definition WebSocket error:', error);
        updateConnectionStatus('definitions', ConnectionStatus.DISCONNECTED);
    };
}

// Set up WebSocket connection for process instances
function setupProcessInstanceWebSocket(definitionId) {
    if (state.connections.instances) {
        state.connections.instances.close();
    }
    
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/process-instances/${definitionId}`;
    
    updateConnectionStatus('instances', ConnectionStatus.CONNECTING);
    
    const socket = new WebSocket(wsUrl);
    state.connections.instances = socket;
    
    socket.onopen = function() {
        console.log(`Process instance WebSocket connection established for ${definitionId}`);
        updateConnectionStatus('instances', ConnectionStatus.CONNECTED);
    };
    
    socket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        handleProcessInstanceMessage(message);
    };
    
    socket.onclose = function() {
        console.log(`Process instance WebSocket connection closed for ${definitionId}`);
        updateConnectionStatus('instances', ConnectionStatus.DISCONNECTED);
    };
    
    socket.onerror = function(error) {
        console.error(`Process instance WebSocket error for ${definitionId}:`, error);
        updateConnectionStatus('instances', ConnectionStatus.DISCONNECTED);
    };
}

// Set up WebSocket connection for flow node instances
function setupFlowNodeInstanceWebSocket(instanceId) {
    if (state.connections.flowNodes) {
        state.connections.flowNodes.close();
    }
    
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/flow-node-instances/${instanceId}`;
    
    updateConnectionStatus('flowNodes', ConnectionStatus.CONNECTING);
    
    const socket = new WebSocket(wsUrl);
    state.connections.flowNodes = socket;
    
    socket.onopen = function() {
        console.log(`Flow node instance WebSocket connection established for ${instanceId}`);
        updateConnectionStatus('flowNodes', ConnectionStatus.CONNECTED);
    };
    
    socket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        handleFlowNodeInstanceMessage(message);
    };
    
    socket.onclose = function() {
        console.log(`Flow node instance WebSocket connection closed for ${instanceId}`);
        updateConnectionStatus('flowNodes', ConnectionStatus.DISCONNECTED);
    };
    
    socket.onerror = function(error) {
        console.error(`Flow node instance WebSocket error for ${instanceId}:`, error);
        updateConnectionStatus('flowNodes', ConnectionStatus.DISCONNECTED);
    };
}

/**
 * WebSocket Message Handlers
 */

// Handle process definition messages from WebSocket
function handleProcessDefinitionMessage(message) {
    console.log('Received process definition message:', message);
    
    if (message.type === 'initial') {
        // Initial load of process definitions (if needed, we already load from REST API)
        // Could merge with existing definitions if needed
    } else if (message.type === 'update') {
        // Update a process definition
        const key = message.key;
        const definition = message.definition;
        
        if (!state.processDefinitions[key.processDefinitionId]) {
            state.processDefinitions[key.processDefinitionId] = {};
        }
        
        state.processDefinitions[key.processDefinitionId][key.version] = definition;
        renderProcessDefinitions();
    }
}

// Handle process instance messages from WebSocket
function handleProcessInstanceMessage(message) {
    console.log('Received process instance message:', message);
    
    if (message.type === 'initial') {
        // Initial load of process instances for selected definition
        const definitionId = message.processDefinitionId;
        state.processInstances[definitionId] = message.data;
        renderProcessInstances(definitionId);
    } else if (message.type === 'update') {
        // New process instance update
        const definitionId = message.processDefinitionId;
        const instanceUpdate = message.data;
        
        if (!state.processInstances[definitionId]) {
            state.processInstances[definitionId] = [];
        }
        
        // Add to beginning of array (newest first)
        state.processInstances[definitionId].unshift(instanceUpdate);
        
        // Limit to 100 instances
        if (state.processInstances[definitionId].length > 100) {
            state.processInstances[definitionId] = state.processInstances[definitionId].slice(0, 100);
        }
        
        renderProcessInstances(definitionId);
    }
}

// Handle flow node instance messages from WebSocket
function handleFlowNodeInstanceMessage(message) {
    console.log('Received flow node instance message:', message);
    
    if (message.type === 'flow-node-update') {
        // Update BPMN diagram based on flow node update
        highlightFlowNode(message.elementId);
    }
}

/**
 * UI Rendering Functions
 */

// Render process definitions list
function renderProcessDefinitions() {
    const container = document.getElementById('processDefinitionsList');
    
    if (Object.keys(state.processDefinitions).length === 0) {
        container.innerHTML = '<div class="p-3 text-muted">No process definitions available</div>';
        return;
    }
    
    let html = '';
    
    for (const definitionId in state.processDefinitions) {
        const versions = state.processDefinitions[definitionId];
        
        // Sort versions in descending order
        const sortedVersions = Object.keys(versions).sort((a, b) => b - a);
        
        html += `<div class="list-group-item">
                    <h6>${definitionId}</h6>
                    <div class="version-group">`;
        
        for (const version of sortedVersions) {
            const definition = versions[version];
            const isActive = definitionId === state.currentDefinitionId && 
                             parseInt(version) === state.currentDefinitionVersion;
            
            html += `<div class="process-definition ${isActive ? 'active' : ''}"
                          data-definition-id="${definitionId}"
                          data-version="${version}">
                        <small>Version ${version}</small>
                     </div>`;
        }
        
        html += `</div></div>`;
    }
    
    container.innerHTML = html;
}

// Render process instances list
function renderProcessInstances(definitionId) {
    const container = document.getElementById('processInstancesList');
    
    if (!definitionId || !state.processInstances[definitionId] || 
        state.processInstances[definitionId].length === 0) {
        container.innerHTML = '<div class="p-3 text-muted">No process instances available</div>';
        return;
    }
    
    let html = '';
    
    for (const instance of state.processInstances[definitionId]) {
        const isActive = instance.processInstanceId === state.currentInstanceId;
        const timestamp = new Date(instance.timestamp).toLocaleTimeString();
        
        html += `<div class="list-group-item process-instance ${isActive ? 'active' : ''}"
                      data-instance-id="${instance.processInstanceId}">
                    <div>${instance.processInstanceId}</div>
                    <small class="text-muted">${timestamp}</small>
                </div>`;
    }
    
    container.innerHTML = html;
}

// Update connection status indicator
function updateConnectionStatus(connectionType, status) {
    const statusElement = document.getElementById('connectionStatus');
    const statusTextElement = document.getElementById('connectionText');
    
    if (connectionType === 'definitions') {
        statusElement.className = status;
        
        switch (status) {
            case ConnectionStatus.CONNECTED:
                statusTextElement.textContent = 'Connected';
                break;
            case ConnectionStatus.DISCONNECTED:
                statusTextElement.textContent = 'Disconnected';
                break;
            case ConnectionStatus.CONNECTING:
                statusTextElement.textContent = 'Connecting...';
                break;
        }
    }
}

/**
 * BPMN Viewer Functions
 */

// Initialize the BPMN viewer
function initializeBpmnViewer() {
    state.bpmnViewer = new window.BpmnJS({
        container: '#bpmnCanvas'
    });
}

// Load and display BPMN diagram
function displayBpmnDiagram(definitionId, version) {
    loadBpmnXml(definitionId, version)
        .then(xml => {
            return state.bpmnViewer.importXML(xml);
        })
        .then(() => {
            const canvas = state.bpmnViewer.get('canvas');
            canvas.zoom('fit-viewport');
        })
        .catch(error => {
            console.error('Error loading BPMN diagram:', error);
            alert('Failed to load BPMN diagram');
        });
}

// Highlight a flow node in the BPMN diagram
function highlightFlowNode(elementId) {
    if (!state.bpmnViewer) return;
    
    const canvas = state.bpmnViewer.get('canvas');
    
    // Clear any previous highlights
    clearBpmnHighlights();
    
    // Highlight the active flow node
    canvas.addMarker(elementId, 'highlight');
}

// Clear highlights from BPMN diagram
function clearBpmnHighlights() {
    if (!state.bpmnViewer) return;
    
    const canvas = state.bpmnViewer.get('canvas');
    const registry = state.bpmnViewer.get('elementRegistry');
    
    registry.forEach(element => {
        canvas.removeMarker(element.id, 'highlight');
    });
}

/**
 * User Interaction Handlers
 */

// Select a process definition
function selectProcessDefinition(definitionId, version) {
    // Update state
    state.currentDefinitionId = definitionId;
    state.currentDefinitionVersion = version;
    
    // Update UI
    renderProcessDefinitions();
    document.getElementById('selectedDefinitionLabel').textContent = 
        `${definitionId} (Version ${version})`;
    
    // Connect to process instance websocket
    setupProcessInstanceWebSocket(definitionId);
    
    // Clear process instance selection
    state.currentInstanceId = null;
    document.getElementById('selectedInstanceLabel').textContent = 'Select a process instance';
    
    // Close flow node websocket if open
    if (state.connections.flowNodes) {
        state.connections.flowNodes.close();
        state.connections.flowNodes = null;
    }
    
    // Load BPMN diagram for this definition
    displayBpmnDiagram(definitionId, version);
}

// Select a process instance
function selectProcessInstance(instanceId) {
    // Update state
    state.currentInstanceId = instanceId;
    
    // Update UI
    renderProcessInstances(state.currentDefinitionId);
    document.getElementById('selectedInstanceLabel').textContent = `Instance: ${instanceId}`;
    
    // Connect to flow node websocket
    setupFlowNodeInstanceWebSocket(instanceId);
}

// Add custom CSS for BPMN highlighting
const style = document.createElement('style');
style.innerHTML = `
  .highlight:not(.djs-connection) .djs-visual > :nth-child(1) {
    fill: #52B415 !important;
  }

  .highlight.djs-connection .djs-visual > :nth-child(1) {
    stroke: #52B415 !important;
  }
`;
document.head.appendChild(style);
