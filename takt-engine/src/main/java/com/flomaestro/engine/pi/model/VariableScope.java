package com.flomaestro.engine.pi.model;

import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;

public interface VariableScope {
  void merge(VariablesDTO variables);
}
