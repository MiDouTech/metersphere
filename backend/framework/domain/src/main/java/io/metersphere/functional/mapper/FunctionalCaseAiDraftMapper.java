package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalCaseAiDraft;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FunctionalCaseAiDraftMapper {
    int insert(FunctionalCaseAiDraft record);

    FunctionalCaseAiDraft selectByPrimaryKey(@Param("id") String id);

    int updateByPrimaryKeySelective(FunctionalCaseAiDraft record);

    int updateByPrimaryKeyAndVersionSelective(@Param("record") FunctionalCaseAiDraft record,
                                              @Param("version") Integer version);

    int markDeleted(@Param("id") String id,
                    @Param("projectId") String projectId,
                    @Param("createUser") String createUser,
                    @Param("updateTime") Long updateTime);

    List<FunctionalCaseAiDraft> selectByIds(@Param("ids") List<String> ids,
                                            @Param("projectId") String projectId,
                                            @Param("createUser") String createUser);

    List<FunctionalCaseAiDraft> selectByIdsInProject(@Param("ids") List<String> ids,
                                                     @Param("projectId") String projectId);

    List<FunctionalCaseAiDraft> selectByProjectAndCreateUser(@Param("projectId") String projectId,
                                                             @Param("createUser") String createUser,
                                                             @Param("draftStatus") String draftStatus,
                                                             @Param("offset") long offset,
                                                             @Param("pageSize") long pageSize);

    long countByProjectAndCreateUser(@Param("projectId") String projectId,
                                     @Param("createUser") String createUser,
                                     @Param("draftStatus") String draftStatus);

    List<FunctionalCaseAiDraft> selectReviewQueue(@Param("projectId") String projectId,
                                                   @Param("draftStatus") String draftStatus,
                                                   @Param("offset") long offset,
                                                   @Param("pageSize") long pageSize);

    long countReviewQueue(@Param("projectId") String projectId,
                          @Param("draftStatus") String draftStatus);

    long countDuplicateByFingerprint(@Param("projectId") String projectId,
                                     @Param("createUser") String createUser,
                                     @Param("fingerprint") String fingerprint,
                                     @Param("excludeId") String excludeId);

    int updateReview(@Param("id") String id,
                     @Param("projectId") String projectId,
                     @Param("version") Integer version,
                     @Param("reviewStatus") String reviewStatus,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewedBy") String reviewedBy,
                     @Param("reviewedAt") Long reviewedAt,
                     @Param("reviewedContentHash") String reviewedContentHash,
                     @Param("updateTime") Long updateTime);

    void insertReviewHistory(@Param("id") String id,
                             @Param("draftId") String draftId,
                             @Param("projectId") String projectId,
                             @Param("action") String action,
                             @Param("comment") String comment,
                             @Param("contentHash") String contentHash,
                             @Param("reviewer") String reviewer,
                             @Param("createTime") Long createTime);
}
