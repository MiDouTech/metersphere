package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.AiSourceDocument;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AiSourceDocumentMapper {
    int insert(AiSourceDocument record);

    AiSourceDocument selectByPrimaryKey(@Param("id") String id);

    int updateByPrimaryKeySelective(AiSourceDocument record);

    AiSourceDocument selectReusableBySha256(@Param("projectId") String projectId,
                                            @Param("createUser") String createUser,
                                            @Param("sha256") String sha256);

    List<AiSourceDocument> selectByIds(@Param("ids") List<String> ids,
                                       @Param("projectId") String projectId,
                                       @Param("createUser") String createUser);

    List<AiSourceDocument> selectByIdsInProject(@Param("ids") List<String> ids,
                                                @Param("projectId") String projectId);

    List<AiSourceDocument> selectByProjectAndCreateUser(@Param("projectId") String projectId,
                                                        @Param("createUser") String createUser,
                                                        @Param("parseStatus") String parseStatus,
                                                        @Param("offset") long offset,
                                                        @Param("pageSize") long pageSize);

    long countByProjectAndCreateUser(@Param("projectId") String projectId,
                                     @Param("createUser") String createUser,
                                     @Param("parseStatus") String parseStatus);

    int markDeleted(@Param("id") String id,
                    @Param("projectId") String projectId,
                    @Param("createUser") String createUser,
                    @Param("updateTime") Long updateTime);
}
