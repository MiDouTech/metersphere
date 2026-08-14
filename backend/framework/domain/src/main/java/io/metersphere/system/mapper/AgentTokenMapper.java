package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentToken;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTokenMapper {
    AgentToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    AgentToken selectByPublicId(@Param("publicId") String publicId);

    AgentToken selectByPrimaryKey(@Param("id") String id);

    int insert(AgentToken record);

    int updateByPrimaryKeySelective(AgentToken record);

    int updateSecret(AgentToken record);

    int markUsed(AgentToken record);

    /** 更新可访问项目（允许将 project_id / project_ids 置空表示全部项目） */
    int updateProjectAccess(AgentToken record);

    int deleteByPrimaryKey(@Param("id") String id);

    List<AgentToken> selectPage(@Param("keyword") String keyword,
                              @Param("status") String status,
                              @Param("now") long now,
                              @Param("offset") long offset,
                              @Param("pageSize") long pageSize);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("now") long now);

    List<AgentToken> selectUserPage(@Param("userId") String userId,
                                    @Param("keyword") String keyword,
                                    @Param("offset") long offset,
                                    @Param("pageSize") long pageSize);

    long countUserPage(@Param("userId") String userId, @Param("keyword") String keyword);
}
