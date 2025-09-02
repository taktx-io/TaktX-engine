/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utility class for JSON conversion.
 * Handles the conversion between array-formatted DTOs and standard JSON objects.
 */
public class JsonUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    /**
     * Converts an object to a JSON string.
     * 
     * @param object The object to convert
     * @return JSON string representation of the object
     */
    public static String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
    
    /**
     * Converts a JSON string to an object of the specified type.
     * 
     * @param <T> The type to convert to
     * @param json The JSON string
     * @param valueType The class of the type
     * @return The object of type T
     */
    public static <T> T fromJsonString(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }
}
