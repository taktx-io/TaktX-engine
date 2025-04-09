package io.taktx.xml;

import io.taktx.bpmn.TRootElement;
import io.taktx.dto.ProcessDTO;

public interface RootElementMapper {

  ProcessDTO map(TRootElement tRootElement);
}
