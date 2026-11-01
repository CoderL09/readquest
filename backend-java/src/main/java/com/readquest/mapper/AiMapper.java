package com.readquest.mapper;

import com.readquest.pojo.AiAnalysisCache;
import org.apache.ibatis.annotations.Param;

public interface AiMapper {
  AiAnalysisCache cached(@Param("user") String user, @Param("hash") String hash);

  int save(AiAnalysisCache cache);

  int cleanup();
}
