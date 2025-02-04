package com.flomaestro.takt.dto.v_1_0_0;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class ThrowEventInstanceDTO extends EventInstanceDTO {}
