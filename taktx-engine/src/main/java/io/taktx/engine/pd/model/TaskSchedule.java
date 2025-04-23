package io.taktx.engine.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TaskSchedule {
    private String dueDate;
    private String followUpDate;
}
