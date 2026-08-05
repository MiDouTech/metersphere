package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalCaseAiGeneration;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FunctionalCaseAiGenerationMapper {
    int insert(FunctionalCaseAiGeneration record);

    FunctionalCaseAiGeneration selectByPrimaryKey(@Param("id") String id);

    int updateByPrimaryKeySelective(FunctionalCaseAiGeneration record);

    List<FunctionalCaseAiGeneration> selectByProjectAndCreateUser(@Param("projectId") String projectId,
                                                                  @Param("createUser") String createUser,
                                                                  @Param("offset") long offset,
                                                                  @Param("pageSize") long pageSize);
}
