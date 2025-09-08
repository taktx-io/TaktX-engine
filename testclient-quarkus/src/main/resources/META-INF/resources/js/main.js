// Constants
const WS_PROCESS_DEFINITIONS_URL = `ws://${window.location.host}/ws/process-definitions`;
const WS_PROCESS_INSTANCE_URL = `ws://${window.location.host}/ws/process-instance`;
const PROCESS_DEFINITIONS_API_URL = '/processdefinitions';
const PROCESS_INSTANCES_API_URL = '/processinstances';
const FLOW_NODE_INSTANCES_API_URL = '/processinstances';

// Global variables
let bpmnViewer;
let processDefinitionsData = {};
let selectedProcessDefinition = null;
let selectedProcessInstance = null;
let selectedFlowNodeInstance = null;
let websocket = null;
let flowNodeWebSocket = null;
let countersIntervalId = null;
let lastCounterUpdateTime = null;

// Flow node instances data structure - organized by flowNodeInstancePath
let flowNodeInstances = new Map();

// Process instances data structure - organized by processInstanceId for real-time updates
let processInstances = new Map();

// WebSocket connection management
function connectWebSocket() {
    if (websocket) {
        websocket.close();
    }
    
    websocket = new WebSocket(WS_PROCESS_DEFINITIONS_URL);
    
    websocket.onopen = () => {
        console.log('WebSocket connection established');
    };
    
    websocket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        if (data.type === 'processDefinitionCounts') {
            updateProcessDefinitionCounts(data.data);
        } else if (data.type === 'processInstanceUpdate') {
            handleProcessInstanceUpdate(data);
        }
    };
    
    websocket.onclose = () => {
        console.log('WebSocket connection closed');
        // Try to reconnect after 5 seconds
        setTimeout(connectWebSocket, 5000);
    };
    
    websocket.onerror = (error) => {
        console.error('WebSocket error:', error);
        websocket.close();
    };
}

// Flow Node WebSocket connection management
function connectFlowNodeWebSocket(processInstanceId) {
    // Close existing connection if any
    if (flowNodeWebSocket) {
        flowNodeWebSocket.close();
    }
    
    console.log('Connecting to FlowNodeInstanceWebSocket for process instance:', processInstanceId);
    flowNodeWebSocket = new WebSocket(WS_PROCESS_INSTANCE_URL + '/' + processInstanceId);
    
    flowNodeWebSocket.onopen = () => {
        console.log('FlowNodeInstanceWebSocket connection established');
        // Subscribe to updates for this process instance
        const subscribeMessage = {
            action: 'subscribe',
            processInstanceId: processInstanceId
        };
        flowNodeWebSocket.send(JSON.stringify(subscribeMessage));
    };
    
    flowNodeWebSocket.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            console.log('Received flow node update:', data);
            
            if (data.type === 'flowNodeInstanceUpdate') {
                handleFlowNodeUpdate(data);
            }
            // Note: processInstanceUpdate is now handled by the global websocket
        } catch (error) {
            console.error('Error parsing flow node WebSocket message:', error);
        }
    };
    
    flowNodeWebSocket.onclose = () => {
        console.log('FlowNodeInstanceWebSocket connection closed');
        // Try to reconnect after 5 seconds if we still have a selected process instance
    };
    
    flowNodeWebSocket.onerror = (error) => {
        console.error('FlowNodeInstanceWebSocket error:', error);
        flowNodeWebSocket.close();
    };
}

// Disconnect from flow node WebSocket
function disconnectFlowNodeWebSocket() {
    if (flowNodeWebSocket) {
        console.log('Disconnecting from FlowNodeInstanceWebSocket');
        flowNodeWebSocket.close();
        flowNodeWebSocket = null;
    }
}

// Handle real-time process instance updates
function handleProcessInstanceUpdate(data) {
    console.log('Processing process instance update:', data);
    
    if (data.update && data.processInstanceId) {
        // Store the process instance update
        processInstances.set(data.processInstanceId, {
            processInstanceId: data.processInstanceId,
            update: data.update,
            timestamp: data.timestamp
        });
        
        // Update the state pill in the process instance list
        updateProcessInstanceStatePill(data.processInstanceId, data.update);
    }
}

// Handle real-time flow node updates
function handleFlowNodeUpdate(data) {
    console.log('Processing flow node update:', data);
    
    // Only process updates for the currently selected process instance
    if (!selectedProcessInstance || selectedProcessInstance.processInstanceId !== data.processInstanceId) {
        return;
    }
    
    if (data.update && data.update.flowNodeInstancePath) {
        // Store the flow node instance update using the path as key
        const pathKey = data.update.flowNodeInstancePath.join('.');
        flowNodeInstances.set(pathKey, {
            path: data.update.flowNodeInstancePath,
            flowNodeInstance: data.update.flowNodeInstance,
            variables: data.update.variables,
            processTime: data.update.processTime,
            timestamp: data.timestamp
        });
        
        // Re-render the flow node instances list
        renderFlowNodeInstancesList();
        
        // Update BPMN diagram highlighting
        updateBpmnHighlighting();
    }
}

// Update process instance state pill in real-time
function updateProcessInstanceStatePill(processInstanceId, processInstanceUpdate) {
    // Find the process instance row in the DOM
    const processInstanceRows = document.querySelectorAll('.process-instance-row');
    
    processInstanceRows.forEach(row => {
        const rowProcessInstanceId = row.getAttribute('data-instance-id');
        if (rowProcessInstanceId === processInstanceId) {
            // Find the status badge within this row
            const statusBadge = row.querySelector('.status-badge');
            if (statusBadge && processInstanceUpdate.flowNodeInstances) {
                const newState = processInstanceUpdate.flowNodeInstances.state;
                const stateClass = getStateClass(newState);
                
                // Update the badge classes and text
                statusBadge.className = `status-badge status-${stateClass}`;
                statusBadge.textContent = newState;
                
                console.log(`Updated process instance ${processInstanceId} state to ${newState}`);
            }
        }
    });
}

// Clear flow node instances list and disable refresh button
function clearFlowNodeInstancesList() {
    const container = document.getElementById('flow-node-instances-list');
    container.innerHTML = '<p class="text-muted">Select a process instance to view flow nodes</p>';
    
    // Clear the flow node instances data
    flowNodeInstances.clear();
}

// Render the flow node instances list in a tree structure
function renderFlowNodeInstancesList() {
    const container = document.getElementById('flow-node-instances-list');
    
    if (flowNodeInstances.size === 0) {
        container.innerHTML = '<p class="text-muted">No flow node instances found</p>';
        return;
    }
    
    // Convert Map to array and sort by path sequence (which indicates execution order)
    const instances = Array.from(flowNodeInstances.values()).sort((a, b) => {
        // Compare paths element by element - the numbers indicate execution sequence
        const minLength = Math.min(a.path.length, b.path.length);
        
        for (let i = 0; i < minLength; i++) {
            if (a.path[i] !== b.path[i]) {
                return a.path[i] - b.path[i]; // Sort by sequence number
            }
        }
        
        // If paths are identical up to this point, shorter path comes first (parent before child)
        return a.path.length - b.path.length;
    });
    
    let html = '<div class="flow-node-tree">';
    
    instances.forEach(instance => {
        const depth = instance.path.length - 1; // Root level is 0
        const indentClass = `indent-${Math.min(depth, 5)}`; // Limit indent levels
        const stateClass = getFlowNodeStateClass(instance.flowNodeInstance);
        const stateIcon = getFlowNodeStateIcon(instance.flowNodeInstance);
        
        html += `
            <div class="flow-node-item ${indentClass}" data-path="${instance.path.join('.')}">
                <div class="flow-node-info">
                    <div class="flow-node-header">
                        <span class="state-icon ${stateClass}">${stateIcon}</span>
                        <span class="element-id">${instance.flowNodeInstance.elementId || 'N/A'}</span>
                        <span class="instance-id">#${instance.flowNodeInstance.elementInstanceId}</span>
                    </div>
                    <div class="flow-node-details">
                        <small class="text-muted">
                            Path: [${instance.path.join(' → ')}] |                              
                            Updated: ${formatTimestamp(instance.timestamp)}
                        </small>
                    </div>
                    ${renderVariables(instance.variables)}
                </div>
            </div>
        `;
    });
    
    html += '</div>';
    container.innerHTML = html;
}

// Get CSS class for flow node state
function getFlowNodeStateClass(flowNodeInstance) {
    if (!flowNodeInstance) return 'unknown';
    
    // Check different state properties based on the flow node type
    if (flowNodeInstance.state) {
        switch(flowNodeInstance.state.toUpperCase()) {
            case 'ACTIVE':
            case 'WAITING':
                return 'active';
            case 'COMPLETED':
            case 'ENDED':
                return 'completed';
            case 'TERMINATED':
            case 'FAILED':
            case 'ERROR':
                return 'terminated';
            default:
                return 'unknown';
        }
    }
    
    // Fallback to checking boolean methods if available
    if (flowNodeInstance.isWaiting && flowNodeInstance.isWaiting()) return 'active';
    if (flowNodeInstance.isTerminated && flowNodeInstance.isTerminated()) return 'terminated';
    if (flowNodeInstance.isFailed && flowNodeInstance.isFailed()) return 'terminated';
    
    return 'unknown';
}

// Get icon for flow node state
function getFlowNodeStateIcon(flowNodeInstance) {
    const stateClass = getFlowNodeStateClass(flowNodeInstance);
    
    switch(stateClass) {
        case 'active':
            return '▶';
        case 'completed':
            return '✓';
        case 'terminated':
            return '✗';
        default:
            return '●';
    }
}

// Format process time for display
function formatProcessTime(processTime) {
    if (!processTime || processTime === 0) {
        return '0ms';
    }
    
    if (processTime < 1000) {
        return `${processTime}ms`;
    } else if (processTime < 60000) {
        return `${(processTime / 1000).toFixed(2)}s`;
    } else {
        const minutes = Math.floor(processTime / 60000);
        const seconds = ((processTime % 60000) / 1000).toFixed(2);
        return `${minutes}m ${seconds}s`;
    }
}

// Render variables section
function renderVariables(variables) {
    if (!variables || !variables.variables || Object.keys(variables.variables).length === 0) {
        return '';
    }
    
    let html = '<div class="variables-section">';
    html += '<div class="variables-header"><small><strong>Variables:</strong></small></div>';
    html += '<div class="variables-list">';
    
    Object.entries(variables.variables).forEach(([key, value]) => {
        const displayValue = formatVariableValue(value);
        html += `<div class="variable-item"><small><code>${key}</code>: ${displayValue}</small></div>`;
    });
    
    html += '</div></div>';
    return html;
}

// Format variable value for display
function formatVariableValue(value) {
    if (value === null || value === undefined) {
        return '<em>null</em>';
    }
    
    if (typeof value === 'object') {
        try {
            const jsonStr = JSON.stringify(value);
            if (jsonStr.length > 100) {
                return `<span class="text-truncate" title="${jsonStr}">${jsonStr.substring(0, 100)}...</span>`;
            }
            return jsonStr;
        } catch (e) {
            return '<em>[Object]</em>';
        }
    }
    
    const str = String(value);
    if (str.length > 50) {
        return `<span class="text-truncate" title="${str}">${str.substring(0, 50)}...</span>`;
    }
    
    return str;
}

// Update BPMN diagram highlighting based on current flow node instances
function updateBpmnHighlighting() {
    if (!bpmnViewer || flowNodeInstances.size === 0) {
        return;
    }
    
    // Reset existing highlights
    removeAllTokenHighlights();
    
    // Get active flow node element IDs
    const activeElementIds = [];
    
    flowNodeInstances.forEach(instance => {
        const stateClass = getFlowNodeStateClass(instance.flowNodeInstance);
        if (stateClass === 'active' && instance.flowNodeInstance.elementId) {
            activeElementIds.push(instance.flowNodeInstance.elementId);
        }
    });
    
    // Highlight active elements
    const canvas = bpmnViewer.get('canvas');
    activeElementIds.forEach(elementId => {
        canvas.addMarker(elementId, 'highlight-token');
        canvas.addMarker(elementId, 'highlight-token-pulse');
    });
}


// Load process definitions
async function loadProcessDefinitions() {
    try {
        const response = await fetch(PROCESS_DEFINITIONS_API_URL);
        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
        }
        
        processDefinitionsData = await response.json();
        renderProcessDefinitionsList();
        
        // Select the first process definition by default
        const firstProcessId = Object.keys(processDefinitionsData)[0];
        if (firstProcessId) {
            const firstVersion = Math.max(...Object.keys(processDefinitionsData[firstProcessId]).map(Number));
            selectProcessDefinition(firstProcessId, firstVersion);
        }
    } catch (error) {
        console.error('Error loading process definitions:', error);
        document.getElementById('process-definitions-list').innerHTML = 
            '<div class="alert alert-danger">Failed to load process definitions</div>';
    }
}

// Render the process definitions list
function renderProcessDefinitionsList() {
    const container = document.getElementById('process-definitions-list');
    if (!processDefinitionsData || Object.keys(processDefinitionsData).length === 0) {
        container.innerHTML = '<p class="loading-text">No process definitions found</p>';
        return;
    }
    
    let html = '';
    
    // Sort process definitions alphabetically
    const sortedProcessIds = Object.keys(processDefinitionsData).sort();
    
    sortedProcessIds.forEach(processId => {
        const versions = processDefinitionsData[processId];
        const versionNumbers = Object.keys(versions).map(Number).sort((a, b) => b - a);
        
        versionNumbers.forEach(version => {
            const process = versions[version];
            const processKey = `${processId}.${version}`;
            const isSelected = selectedProcessDefinition && 
                              selectedProcessDefinition.id === processId && 
                              selectedProcessDefinition.version === version;
            
            html += `
                <div class="process-definition-item card p-3 ${isSelected ? 'selected' : ''}" 
                     data-process-id="${processId}" 
                     data-version="${version}"
                     onclick="selectProcessDefinition('${processId}', ${version})">
                    <div class="d-flex justify-content-between">
                        <p class="process-definition-name">${processId}</p>
                        <span class="version-badge">v${version}</span>
                    </div>
                    <div class="counters">
                        <div class="counter counter-started">
                            <span class="counter-icon">▶</span>
                            <span class="counter-value" data-process-key="${processKey}" data-counter="started">0</span>
                            <span class="counter-label">Started</span>
                        </div>
                        <div class="counter counter-completed">
                            <span class="counter-icon">✓</span>
                            <span class="counter-value" data-process-key="${processKey}" data-counter="completed">0</span>
                            <span class="counter-label">Completed</span>
                        </div>
                    </div>
                </div>
            `;
        });
    });
    
    container.innerHTML = html;
}

// Select a process definition and load its BPMN diagram
async function selectProcessDefinition(processId, version) {
    // Update selected state
    selectedProcessDefinition = { id: processId, version: version };
    selectedProcessInstance = null;
    selectedFlowNodeInstance = null;
    
    // Disconnect from any existing flow node WebSocket
    disconnectFlowNodeWebSocket();
    
    // Update UI to show selection
    document.querySelectorAll('.process-definition-item').forEach(item => {
        item.classList.remove('selected');
    });
    
    const selectedItem = document.querySelector(
        `.process-definition-item[data-process-id="${processId}"][data-version="${version}"]`
    );
    if (selectedItem) {
        selectedItem.classList.add('selected');
    }
    
    // Update the diagram title
    document.getElementById('diagram-title').innerText = 
        `BPMN Diagram: ${processId} (v${version})`;
    
    // Clear flow node instances list and disable refresh button
    clearFlowNodeInstancesList();
    
    // Load and display BPMN diagram
    await loadBpmnDiagram(processId, version);

    // Load process instances for this definition
    loadProcessInstances(processId, version);
}

async function fetchFlowNodeInstances(processInstanceId) {
    const flowNodeContainer = document.getElementById('flow-node-instances-list');
    flowNodeContainer.innerHTML = '<p class="loading-text">Loading flow node instances...</p>';

    const response = await fetch(`${FLOW_NODE_INSTANCES_API_URL}/${processInstanceId}/flownodes`);
    if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
    }

    return await response.json();
}

// Initialize BPMN.js viewer
function initBpmnViewer() {
    bpmnViewer = new BpmnJS({
        container: '#bpmn-canvas'
    });
    
    // Handle viewer errors
    bpmnViewer.on('import.done', function(event) {
        if (event.error) {
            console.error('Error rendering BPMN diagram:', event.error);
        } else {
            const canvas = bpmnViewer.get('canvas');
            canvas.zoom('fit-viewport');
            
            // If a process instance is selected, highlight its tokens
            if (selectedProcessInstance) {
                highlightTokensForInstance(selectedProcessInstance);
            }
        }
    });
}

// Load BPMN diagram from server
async function loadBpmnDiagram(processId, version) {
    try {
        const response = await fetch(`${PROCESS_DEFINITIONS_API_URL}/${processId}.${version}`);
        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
        }
        
        const bpmnXml = await response.text();
        
        // Import BPMN XML
        await bpmnViewer.importXML(bpmnXml);
    } catch (error) {
        console.error('Error loading BPMN diagram:', error);
        document.getElementById('bpmn-canvas').innerHTML = 
            '<div class="alert alert-danger m-3">Failed to load BPMN diagram</div>';
    }
}

// Load process instances for a specific process definition
async function loadProcessInstances(processId, version, limit = 100) {
    try {
        const instancesContainer = document.getElementById('process-instances-list');
        instancesContainer.innerHTML = '<p class="loading-text">Loading process instances...</p>';
        
        const response = await fetch(`${PROCESS_INSTANCES_API_URL}/${processId}/${version}?limit=${limit}`);
        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
        }
        
        const instances = await response.json();
        renderProcessInstancesList(instances);
    } catch (error) {
        console.error('Error loading process instances:', error);
        document.getElementById('process-instances-list').innerHTML = 
            '<div class="alert alert-danger">Failed to load process instances</div>';
    }
}

// Render the list of process instances
function renderProcessInstancesList(instances) {
    const container = document.getElementById('process-instances-list');
    
    if (!instances || instances.length === 0) {
        container.innerHTML = '<p class="text-muted">No process instances found</p>';
        return;
    }
    
    let html = '';
    
    instances.forEach(instance => {
        const isSelected = selectedProcessInstance && selectedProcessInstance.processInstanceId === instance.processInstanceId;
        const stateClass = getStateClass(instance.update.flowNodeInstances.state);
        
        html += `
            <div class="process-instance-row" data-instance-id="${instance.processInstanceId}" onclick="selectProcessInstance('${instance.processInstanceId}')">
                <div class="process-instance-info ${isSelected ? 'selected' : ''}">
                    <div class="id-time">
                        <div class="uuid">${formatUuid(instance.processInstanceId)}</div>
                        <div class="timestamp">${formatTimestamp(instance.timestamp)}</div>
                    </div>
                    <div class="status-badge status-${stateClass}">${instance.update.flowNodeInstances.state}</div>
                </div>
            </div>
        `;
    });
    
    container.innerHTML = html;
    
    // Store the rendered instances for potential real-time updates
    instances.forEach(instance => {
        processInstances.set(instance.processInstanceId, {
            processInstanceId: instance.processInstanceId,
            update: instance.update,
            timestamp: instance.timestamp
        });
    });
}

// Format UUID for display
function formatUuid(uuid) {
    if (!uuid) return '';
    return uuid; // Return the full UUID without truncation
}

// Format timestamp to a human-readable date
function formatTimestamp(timestamp) {
    if (!timestamp || timestamp === 0) {
        return 'N/A';
    }
    
    // Convert milliseconds to seconds if needed (timestamps over year 2001 are likely in milliseconds)
    if (timestamp > 31536000000) { // More than year 2001
        return new Date(timestamp).toLocaleString();
    } else {
        return new Date(timestamp * 1000).toLocaleString();
    }
}

// Get CSS class for process instance state
function getStateClass(state) {
    if (!state) return 'unknown';
    
    switch(state.toUpperCase()) {
        case 'ACTIVE':
            return 'active';
        case 'COMPLETED':
        case 'ENDED':
            return 'completed';
        case 'TERMINATED':
        case 'ERROR':
            return 'terminated';
        default:
            return 'unknown';
    }
}

// Select a process instance and highlight its tokens in the BPMN diagram
async function selectProcessInstance(instanceId) {
    // Update selected state
    console.log("selectProcessInstance")
    selectedProcessInstance = { processInstanceId: instanceId };
    selectedFlowNodeInstance = null;
    
    // Disconnect from previous flow node WebSocket and connect to new one
    disconnectFlowNodeWebSocket();
    clearFlowNodeInstancesList();
    connectFlowNodeWebSocket(instanceId);
    
    // Update UI to show selection
    document.querySelectorAll('.process-instance-row').forEach(item => {
        item.querySelector('.process-instance-info').classList.remove('selected');
    });
    
    const selectedItem = document.querySelector(
        `.process-instance-row[data-instance-id="${instanceId}"]`
    );
    if (selectedItem) {
        selectedItem.querySelector('.process-instance-info').classList.add('selected');
    }
}

// Highlight tokens in the BPMN diagram for a process instance
function highlightTokensForInstance(processInstance, flowNodeInstances) {
    if (!bpmnViewer || !processInstance || !flowNodeInstances) {
        return;
    }
    
    // Reset any existing highlights
    removeAllTokenHighlights();
    
    // Get the active flow nodes
    const activeFlowNodeIds = [];
    
    if (processInstance.flowNodeInstances && flowNodeInstances) {

        // Extract active flow node IDs
        Object.values(flowNodeInstances).forEach(node => {
            if (node.flowNodeInstance.state === 'WAITING') {
                activeFlowNodeIds.push(node.flowNodeInstance.elementId);
            }
        });
    }
    
    // Highlight the active flow nodes
    const canvas = bpmnViewer.get('canvas');
    activeFlowNodeIds.forEach(flowNodeId => {
        canvas.addMarker(flowNodeId, 'highlight-token');
        canvas.addMarker(flowNodeId, 'highlight-token-pulse');
    });
}

// Remove all token highlights from the BPMN diagram
function removeAllTokenHighlights() {
    if (!bpmnViewer) {
        return;
    }
    
    const canvas = bpmnViewer.get('canvas');
    const registry = bpmnViewer.get('elementRegistry');
    
    registry.forEach(element => {
        canvas.removeMarker(element.id, 'highlight-token');
        canvas.removeMarker(element.id, 'highlight-token-pulse');
    });
}

// Update process definition counts
function updateProcessDefinitionCounts(countsData) {
    lastCounterUpdateTime = Date.now();
    
    // Convert the countsData keys to our format (processId.version)
    const processKeys = Object.keys(countsData);
    
    processKeys.forEach(key => {
        const [processId, version] = key.split('.');
        const processKey = `${processId}.${version}`;
        const counts = countsData[key];
        
        // Store the counts in our data model
        if (!processDefinitionsData[processId]) {
            processDefinitionsData[processId] = {};
        }
        
        if (!processDefinitionsData[processId][version]) {
            processDefinitionsData[processId][version] = { counts: {} };
        }
        
        processDefinitionsData[processId][version].counts = {
            started: counts.started,
            completed: counts.completed
        };
        
        // Update the UI elements
        const startedEl = document.querySelector(`.counter-value[data-process-key="${processKey}"][data-counter="started"]`);
        const completedEl = document.querySelector(`.counter-value[data-process-key="${processKey}"][data-counter="completed"]`);
        
        if (startedEl) startedEl.textContent = counts.started;
        if (completedEl) completedEl.textContent = counts.completed;
    });
}

// Simulate counter increments between server updates
function startCounterAnimation() {
    if (countersIntervalId) {
        clearInterval(countersIntervalId);
    }
    
    countersIntervalId = setInterval(() => {
        if (!lastCounterUpdateTime) return;
        
        const timeSinceUpdate = Date.now() - lastCounterUpdateTime;
        const updateInterval = 1000; // 1 second in milliseconds
        const progress = Math.min(timeSinceUpdate / updateInterval, 1);
        
        for (const processId in processDefinitionsData) {
            for (const version in processDefinitionsData[processId]) {
                const process = processDefinitionsData[processId][version];
                if (!process.counts) continue;
                
                const processKey = `${processId}.${version}`;
                const startedEl = document.querySelector(`.counter-value[data-process-key="${processKey}"][data-counter="started"]`);
                const completedEl = document.querySelector(`.counter-value[data-process-key="${processKey}"][data-counter="completed"]`);
                
                // Estimate current value based on progress between updates
                if (process.prevCounts && process.counts) {
                    const startedDiff = process.counts.started - process.prevCounts.started;
                    const completedDiff = process.counts.completed - process.prevCounts.completed;
                    
                    // Only animate if there was an increase
                    if (startedDiff > 0 && startedEl) {
                        const animated = Math.floor(process.prevCounts.started + (startedDiff * progress));
                        startedEl.textContent = animated;
                    }
                    
                    if (completedDiff > 0 && completedEl) {
                        const animated = Math.floor(process.prevCounts.completed + (completedDiff * progress));
                        completedEl.textContent = animated;
                    }
                }
            }
        }
    }, 100); // Update animation every 100ms for smooth appearance
}

// Store previous counts before updating
function storeCurrentCountsAsHistory() {
    for (const processId in processDefinitionsData) {
        for (const version in processDefinitionsData[processId]) {
            const process = processDefinitionsData[processId][version];
            if (process.counts) {
                process.prevCounts = { ...process.counts };
            }
        }
    }
}

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    // Initialize BPMN.js viewer
    initBpmnViewer();
    
    // Load process definitions
    loadProcessDefinitions();
    
    // Connect to WebSocket for real-time updates
    connectWebSocket();
    
    // Setup interval for calculating changes between WebSocket updates
    setInterval(storeCurrentCountsAsHistory, 1000);
    
    // Start counter animation
    startCounterAnimation();
    
    // Add event listener for refresh button
    document.getElementById('refresh-instances-btn').addEventListener('click', () => {
        if (selectedProcessDefinition) {
            loadProcessInstances(
                selectedProcessDefinition.id, 
                selectedProcessDefinition.version
            );
        }
    });

    // Initialize resizable panels
    initResizablePanels();
});

// Clean up WebSocket connections when the page is unloaded
window.addEventListener('beforeunload', function() {
    disconnectFlowNodeWebSocket();
});

// Also clean up on page visibility change (when tab becomes hidden)
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        disconnectFlowNodeWebSocket();
    } else if (selectedProcessInstance && !flowNodeWebSocket) {
        // Reconnect when tab becomes visible again
        connectFlowNodeWebSocket(selectedProcessInstance.id);
    }
});

// Initialize resizable panels
function initResizablePanels() {
    // Horizontal resize for definition panel
    const definitionsPanel = document.getElementById('definitions-panel');
    const contentPanel = document.getElementById('content-panel');
    const horizontalResizeHandle = document.querySelector('.resize-handle-horizontal');
    
    // Vertical resize for instances panel
    const instancesPanel = document.getElementById('instances-panel');
    const instancesContainer = document.getElementById('instances-container');
    const bpmnContainer = document.getElementById('bpmn-container');
    const verticalResizeHandle = document.querySelector('.resize-handle-vertical');
    
    // Track initial positions for dragging
    let startY = 0;
    let startHeight = 0;
    
    if (horizontalResizeHandle) {
        horizontalResizeHandle.addEventListener('mousedown', initHorizontalDrag);
    }
    
    if (verticalResizeHandle) {
        verticalResizeHandle.addEventListener('mousedown', initVerticalDrag);
    }
    
    function initHorizontalDrag(e) {
        e.preventDefault();
        document.addEventListener('mousemove', resizeHorizontal);
        document.addEventListener('mouseup', stopHorizontalDrag);
    }
    
    function initVerticalDrag(e) {
        e.preventDefault();
        startY = e.clientY;
        startHeight = parseInt(window.getComputedStyle(instancesPanel).height);
        document.addEventListener('mousemove', resizeVertical);
        document.addEventListener('mouseup', stopVerticalDrag);
    }
    
    function resizeHorizontal(e) {
        const containerWidth = document.querySelector('.container-fluid').clientWidth;
        let newWidth = (e.clientX / containerWidth) * 100;
        
        // Set min and max limits
        newWidth = Math.max(15, Math.min(newWidth, 80)); // Min 15%, Max 80%
        
        definitionsPanel.style.width = newWidth + '%';
        contentPanel.style.left = newWidth + '%';
        
        // Ensure content panel has minimum width
        const remainingWidth = 100 - newWidth;
        if (remainingWidth < 20) { // Ensure at least 20% width for content panel
            const adjustedWidth = 80;
            definitionsPanel.style.width = adjustedWidth + '%';
            contentPanel.style.left = adjustedWidth + '%';
        }
        
        // Refresh the BPMN diagram after resize
        if (bpmnViewer) {
            bpmnViewer.get('canvas').zoom('fit-viewport');
        }
    }
    
    function resizeVertical(e) {
        // Correct calculation: when moving mouse up (lower Y), the panel height should increase
        const diff = startY - e.clientY; // Positive when moving up, negative when moving down
        const newHeight = startHeight + diff; // Add the difference to increase height when moving up
        
        // Set min and max limits
        const contentPanelHeight = contentPanel.clientHeight;
        const minHeight = 100; // Min 100px for instances panel
        const maxHeight = Math.min(contentPanelHeight * 0.8, window.innerHeight * 0.6); // Max 80% of content panel or 60% of viewport
        
        // Apply new height within limits and ensure it's positive
        if (newHeight >= minHeight && newHeight <= maxHeight && newHeight > 0) {
            instancesPanel.style.height = newHeight + 'px';
            
            // Ensure the instances panel is visible by checking its position
            const panelRect = instancesPanel.getBoundingClientRect();
            if (panelRect.bottom > window.innerHeight) {
                // If panel extends below viewport, adjust height
                const adjustedHeight = window.innerHeight - panelRect.top - 20; // 20px margin
                if (adjustedHeight >= minHeight) {
                    instancesPanel.style.height = adjustedHeight + 'px';
                }
            }
            
            // Refresh the BPMN diagram after resize
            if (bpmnViewer) {
                setTimeout(() => {
                    bpmnViewer.get('canvas').zoom('fit-viewport');
                }, 50);
            }
        }
    }
    
    function stopHorizontalDrag() {
        document.removeEventListener('mousemove', resizeHorizontal);
        document.removeEventListener('mouseup', stopHorizontalDrag);
    }
    
    function stopVerticalDrag() {
        document.removeEventListener('mousemove', resizeVertical);
        document.removeEventListener('mouseup', stopVerticalDrag);
    }
}

// Expose functions to window for inline event handlers
window.selectProcessDefinition = selectProcessDefinition;
window.selectProcessInstance = selectProcessInstance;
