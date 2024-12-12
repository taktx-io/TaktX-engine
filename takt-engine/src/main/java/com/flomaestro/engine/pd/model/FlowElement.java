package com.flomaestro.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowElement extends BaseElement {
  private FlowElement parentElement;
}
