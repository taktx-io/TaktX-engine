package com.flomaestro.engine.pd;

import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;

public record TimedScheduleKey(long time, ScheduleKeyDTO scheduleKey) {}
