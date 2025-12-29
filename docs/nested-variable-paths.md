# Nested Variable Path Support in IoMappingProcessor

## Overview

The `IoMappingProcessor` now supports **dot-notation for nested JSON structures** when mapping variables. This allows you to create and update hierarchical JSON objects directly through variable mapping definitions.

## Feature Description

### Before
Previously, variable names were treated as flat keys:
```java
target = "user" → variables.put("user", value)
```

### After
Now, dot-notation creates nested JSON structures:
```java
target = "user.name" → { "user": { "name": value } }
target = "user.address.city" → { "user": { "address": { "city": value } } }
```

## Key Capabilities

### 1. **Creating Nested Structures**
When a variable path doesn't exist, the full nested structure is automatically created:

```java
IoVariableMapping mapping = new IoVariableMapping("input", "customer.address.city");
// Result: { "customer": { "address": { "city": "New York" } } }
```

### 2. **Merging with Existing Objects**
When the root object already exists, new properties are merged without losing existing data:

```java
// Existing: { "customer": { "id": "123", "status": "active" } }
IoVariableMapping mapping = new IoVariableMapping("input", "customer.name");
// Result: { "customer": { "id": "123", "status": "active", "name": "John" } }
```

### 3. **Multiple Paths to Same Root**
Multiple mappings targeting different paths within the same root object are properly merged:

```java
IoVariableMapping mapping1 = new IoVariableMapping("expr1", "user.name");
IoVariableMapping mapping2 = new IoVariableMapping("expr2", "user.age");
// Result: { "user": { "name": "John", "age": 30 } }
```

### 4. **Deep Nesting**
Supports arbitrary nesting levels:

```java
IoVariableMapping mapping = new IoVariableMapping("source", "level1.level2.level3.level4.value");
// Creates: { "level1": { "level2": { "level3": { "level4": { "value": "..." } } } } }
```

### 5. **Type Replacement**
If a variable exists but is not an ObjectNode (e.g., it's a string or number), it will be replaced with an ObjectNode to accommodate the nested path:

```java
// Existing: { "user": "someString" }
IoVariableMapping mapping = new IoVariableMapping("input", "user.name");
// Result: { "user": { "name": "John" } }  // String replaced with object
```

## Implementation Details

### Method: `setNestedVariable()`

The new private method handles the nested path logic:

1. **Simple Path Detection**: If no dots are present, uses the original behavior
2. **Path Splitting**: Splits the target by dots to create a path array
3. **Root Object Handling**: 
   - Gets existing root object if it exists and is an ObjectNode
   - Creates new ObjectNode if it doesn't exist or isn't an object
4. **Path Navigation**: Iterates through intermediate path parts, creating ObjectNodes as needed
5. **Value Assignment**: Sets the final value at the deepest level
6. **Storage**: Stores the modified root object back to the VariableScope

### Code Structure

```java
private void setNestedVariable(VariableScope variables, String varName, JsonNode value) {
    if (!varName.contains(".")) {
        // Simple case: no nesting
        variables.put(varName, value);
        return;
    }

    String[] pathParts = varName.split("\\.");
    String rootVarName = pathParts[0];
    
    // Get or create root object
    JsonNode rootNode = variables.getVariables().get(rootVarName);
    ObjectNode rootObject = (rootNode != null && rootNode.isObject()) 
        ? (ObjectNode) rootNode 
        : objectMapper.createObjectNode();
    
    // Navigate/create nested path
    ObjectNode currentObject = rootObject;
    for (int i = 1; i < pathParts.length - 1; i++) {
        String key = pathParts[i];
        JsonNode childNode = currentObject.get(key);
        
        if (childNode == null || !childNode.isObject()) {
            ObjectNode newObject = objectMapper.createObjectNode();
            currentObject.set(key, newObject);
            currentObject = newObject;
        } else {
            currentObject = (ObjectNode) childNode;
        }
    }
    
    // Set final value
    String finalKey = pathParts[pathParts.length - 1];
    currentObject.set(finalKey, value);
    
    // Store back to variables
    variables.put(rootVarName, rootObject);
}
```

## Use Cases

### Example 1: Service Response Mapping
```xml
<ioMapping>
  <output source="response.customerId" target="customer.id" />
  <output source="response.name" target="customer.name" />
  <output source="response.email" target="customer.contact.email" />
  <output source="response.phone" target="customer.contact.phone" />
</ioMapping>
```

Result:
```json
{
  "customer": {
    "id": "12345",
    "name": "John Doe",
    "contact": {
      "email": "john@example.com",
      "phone": "+1234567890"
    }
  }
}
```

### Example 2: API Integration
```xml
<ioMapping>
  <input source="order.customer.name" target="apiRequest.customerName" />
  <input source="order.items" target="apiRequest.orderItems" />
  <input source="settings.apiKey" target="apiRequest.authentication.key" />
</ioMapping>
```

## Testing

Comprehensive test coverage includes:
- ✅ Simple nested paths (e.g., `user.name`)
- ✅ Deep nested paths (e.g., `level1.level2.level3.value`)
- ✅ Merging with existing objects
- ✅ Replacing non-object values
- ✅ Multiple paths to same root
- ✅ Mixed simple and nested paths
- ✅ Integration tests with realistic scenarios

## Backward Compatibility

✅ **Fully backward compatible**: Simple variable names (without dots) work exactly as before.

## Performance Considerations

- Dot detection uses `String.contains(".")` for fast simple-case bypass
- Jackson's `ObjectNode` provides efficient mutation
- Path splitting happens only once per variable
- No recursion used, avoiding stack overhead

## Related Files

- **Implementation**: `taktx-engine/src/main/java/io/taktx/engine/pi/processor/IoMappingProcessor.java`
- **Unit Tests**: `taktx-engine/src/test/java/io/taktx/engine/pi/processor/IoMappingProcessorTest.java`
- **Integration Tests**: `taktx-engine/src/test/java/io/taktx/engine/pi/processor/IoMappingProcessorIntegrationTest.java`

