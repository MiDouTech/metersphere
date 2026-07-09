package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentToken;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTokenMapper {
    AgentToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    AgentToken selectByPrimaryKey(@Param("id") String id);

    int insert(AgentToken record);

    int updateByPrimaryKeySelective(AgentToken record);

    int deleteByPrimaryKey(@Param("id") String id);

    List<AgentToken> selectPage(@Param("keyword") String keyword,
                              @Param("offset") long offset,
                              @Param("pageSize") long pageSize);

    long countPage(@Param("keyword") String keyword);
}
