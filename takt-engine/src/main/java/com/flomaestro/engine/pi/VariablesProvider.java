package com.flomaestro.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface VariablesProvider {
  VariablesProvider EMPTY =
      new VariablesProvider() {
        @Override
        public JsonNode get(String key) {
          return null;
        }

        @Override
        public Map<String, JsonNode> getAll() {
          return Map.of();
        }
      };

  JsonNode get(String key);

  Map<String, JsonNode> getAll();
}
