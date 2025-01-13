package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;

public interface IDataService {

  Data createData(long epochMillis, ProcessingStatisticsDTO data) throws DataServiceException;
}
