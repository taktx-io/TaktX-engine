package com.flomaestro.engine.pi;

import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import java.util.UUID;

public record InstanceUpdate(UUID processInstanceKey, InstanceUpdateDTO update) {}
