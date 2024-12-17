package com.flomaestro.engine.pd.model;

import java.util.Optional;

public interface WithErrorEventDefinitions {
  Optional<ErrorEventDefinition> getErrorEventDefinition();
}
