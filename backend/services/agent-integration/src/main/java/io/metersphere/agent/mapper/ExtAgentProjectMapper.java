package io.metersphere.agent.mapper;

import io.metersphere.project.domain.Project;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtAgentProjectMapper {

    List<Project> resolveExact(@Param("projectIds") List<String> projectIds,
                               @Param("identity") String identity, @Param("identityType") String identityType);

    long countSearch(@Param("projectIds") List<String> projectIds,
                     @Param("keyword") String keyword,
                     @Param("likeKeyword") String likeKeyword,
                     @Param("includeArchived") boolean includeArchived);

    List<Project> search(@Param("projectIds") List<String> projectIds,
                         @Param("keyword") String keyword,
                         @Param("likeKeyword") String likeKeyword,
                         @Param("includeArchived") boolean includeArchived,
                         @Param("offset") int offset,
                         @Param("limit") int limit);
}
