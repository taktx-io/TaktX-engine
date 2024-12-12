package com.flomaestro.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Event extends FlowNode implements WithIoMapping {

  private InputOutputMapping ioMapping;
}
