package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalCaseAiGeneration;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FunctionalCaseAiGenerationMapper {
    int insert(FunctionalCaseAiGeneration record);

    FunctionalCaseAiGeneration selectByPrimaryKey(@Param("id") String id);

    int updateByPrimaryKeySelective(FunctionalCaseAiGeneration record);

    int updateTerminalIfActive(FunctionalCaseAiGeneration record);

    int cancelIfActive(@Param("id") String id, @Param("projectId") String projectId,
                       @Param("createUser") String createUser, @Param("updateTime") long updateTime,
                       @Param("errorMessage") String errorMessage);

    List<FunctionalCaseAiGeneration> selectByProjectAndCreateUser(@Param("projectId") String projectId,
                                                                  @Param("createUser") String createUser,
                                                                  @Param("offset") long offset,
                                                                  @Param("pageSize") long pageSize);
}
