package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;

public interface IDataService {

  void createData(long epochMillis, ProcessingStatisticsDTO data) throws DataServiceException;

  void createProcessInstanceData(
      long timestamp, String processDefinitionId, long averageProcessingTime, int size)
      throws DataServiceException;
}
